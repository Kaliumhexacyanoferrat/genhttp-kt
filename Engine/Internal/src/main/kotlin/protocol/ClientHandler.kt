package org.genhttp.engine.internal.protocol

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.internal.context.ConnectionContext
import org.genhttp.engine.internal.infrastructure.ConnectionWatch
import org.genhttp.engine.shared.types.ConnectionExceptions
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.HttpResponseBuilder
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser
import org.genhttp.modules.io.strings.StringContent

import org.slf4j.Logger

import java.io.IOException

import kotlin.time.Duration.Companion.seconds

/**
 * Reads requests from a client connection, passes them to the handler chain and writes the responses.
 */
internal class ClientHandler(private val context: ConnectionContext) {

    // region Functionality

    suspend fun run() {
        // only resolved if an actual error needs to be logged, so well behaved connections never pay for it
        var logger: Logger? = null

        fun logUnlessGraceful(e: Throwable, message: String) {
            if (ConnectionExceptions.isGracefulDisconnect(e)) {
                return
            }

            val current = logger ?: context.server.logging.getLogger(ClientHandler::class.java.name).also { logger = it }
            current.warn(message, e)
        }

        try {
            handleConnection()
        } catch (e: Throwable) {
            logUnlessGraceful(e, "Failed to handle client connection")
        } finally {
            try {
                context.channel.close()
            } catch (e: Exception) {
                logUnlessGraceful(e, "Failed to dispose client stream")
            }

            try {
                context.connection.close()
            } catch (e: Exception) {
                logUnlessGraceful(e, "Failed to shut down client connection")
            }
        }
    }

    private suspend fun handleConnection() {
        val reader = context.reader
        val request = context.request
        val into = request.source
        val watch = context.watch

        // data needs to arrive before this deadline, the watchdog closes the connection otherwise
        var deadline = ConnectionWatch.deadlineAfter(InitialReadTimeout)
        var dataRemaining = false

        try {
            while (context.server.running) {
                if (!dataRemaining) {
                    if (!watch.arm(deadline)) {
                        return
                    }

                    val read = try {
                        reader.fill()
                    } catch (e: IOException) {
                        if (!watch.disarm() || ConnectionExceptions.isGracefulDisconnect(e)) {
                            return
                        }

                        throw e
                    }

                    if (!watch.disarm()) {
                        return
                    }

                    if (read < 0) {
                        break
                    }
                }

                dataRemaining = false

                val bytesRead = UltraHardenedParser.tryExtractFullHeader(reader.data(), into, Limits)

                if (bytesRead < 0) {
                    if (reader.isCompleted) {
                        break
                    }

                    continue
                }

                // the request header stays valid in the buffer until the body is read
                reader.consume(bytesRead + 1)

                request.apply(context.server, context.endPoint, reader, context.remoteAddress, context.clientCertificate)

                val status = handleRequest(request)

                if (!context.connection.isOpen) {
                    return
                }

                if (status == Connection.Upgrade) {
                    // content handler is responsible for flushing, avoid duplicate flushes
                    return
                } else if (status == Connection.Close) {
                    context.writer.flush()
                    return
                }

                request.drain()
                request.reset()

                if (reader.available > 0) {
                    // pipeline mode (more data available)
                    dataRemaining = true
                } else {
                    if (reader.isCompleted) {
                        break
                    }

                    deadline = ConnectionWatch.deadlineAfter(KeepAliveTimeout)
                    context.writer.flush()
                }
            }
        } catch (e: HttpParseException) {
            // client did something wrong
            sendError(e, ResponseStatus.fromCode(e.statusCode) ?: ResponseStatus.BadRequest)
            throw e
        } catch (e: Throwable) {
            if (ConnectionExceptions.isGracefulDisconnect(e)) {
                throw e
            }

            // we did something wrong
            sendError(e, ResponseStatus.InternalServerError)
            throw e
        } finally {
            context.writer.flush()
        }
    }

    internal suspend fun handleRequest(request: HttpRequest): Connection {
        val header = request.header

        val headRequest = header.method == RequestMethod.Head

        val connectionHeader = header.headers.getEntry(KnownHeaders.Connection)

        var keepAliveRequested = true

        if (connectionHeader != null) {
            keepAliveRequested = connectionHeader == KeepAliveValue
        } else if (header.protocol == HttpProtocol.Http10) {
            keepAliveRequested = false
        }

        val response = context.server.handler.handle(request)
            ?: throw IllegalStateException("The root request handler did not return a response")

        val upgradeResponse = response.mode == Connection.Upgrade
        val closeRequested = response.mode == Connection.Close || upgradeResponse

        val active = context.responseHandler.handle(request, response, HttpProtocol.Http11, keepAliveRequested && !closeRequested, headRequest)

        if (upgradeResponse) {
            return Connection.Upgrade
        }

        return if (active && keepAliveRequested && !closeRequested) Connection.KeepAlive else Connection.Close
    }

    private suspend fun sendError(e: Throwable, status: ResponseStatus) {
        try {
            val message = if (context.server.development) e.stackTraceToString() else (e.message ?: e.javaClass.name)

            val response = HttpResponseBuilder()
                .status(status)
                .connection(Connection.Close)
                .content(StringContent(message))
                .build()

            context.responseHandler.handle(null, response, HttpProtocol.Http10, keepAlive = false, headRequest = false)
            context.writer.flush()
        } catch (_: Exception) {
            // no recovery here
        }
    }

    // endregion

    private companion object {

        val InitialReadTimeout = 10.seconds

        val KeepAliveTimeout = 60.seconds

        val KeepAliveValue = ByteString("keep-alive")

        val Limits: ParserLimits = ParserLimits.Default

    }

}
