package org.genhttp.engine.ioxide.protocol

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

import org.genhttp.api.infrastructure.BufferSize
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.ioxide.context.ConnectionContext
import org.genhttp.engine.shared.types.ConnectionExceptions
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.HttpResponseBuilder
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser
import org.genhttp.modules.io.strings.StringContent

import org.slf4j.Logger

import java.util.concurrent.Executors

/**
 * Serves HTTP/1.1 on one connection, request after request (`Http1Driver` of the ioxide engine in GenHTTP).
 *
 * The driver runs as a coroutine on the reactor of the connection. It waits for data (suspending until the ring
 * delivers it), parses the request header, waits until the body has been received completely, passes the request to
 * the handler chain and writes the response. Pipelined requests are handled without waiting, their responses are
 * sent with a single send.
 */
internal class Http1Driver(private val context: ConnectionContext) {

    private val body = BodyBuffering()

    // region Functionality

    /**
     * Resets the state, so that the driver can serve another connection.
     */
    fun reset() {
        body.reset()
    }

    suspend fun run() {
        try {
            handleConnection()
        } catch (e: Throwable) {
            if (!ConnectionExceptions.isGracefulDisconnect(e)) {
                logger().warn("Failed to handle client connection", e)
            }
        }
    }

    private suspend fun handleConnection() {
        var request = context.request
        var into = request.source

        var dataRemaining = false

        // a complete header has been received, but the body has not
        var pending = false

        try {
            while (context.server.running) {
                if (!dataRemaining) {
                    val inbound = context.inbound

                    if (inbound.mustWait()) {
                        // stages the responses before waiting, they are sent once the reactor has handled the current
                        // completions (so pipelined requests received at once are answered with a single send)
                        context.writer.flush()

                        inbound.await()
                    }

                    if (context.reader.fill() < 0 && !pending) {
                        break
                    }
                }

                dataRemaining = false

                val reader = context.reader

                val bytesRead = UltraHardenedParser.tryExtractFullHeader(reader.peek(), into, Limits)

                if (bytesRead < 0) {
                    if (reader.isCompleted) {
                        break
                    }

                    if (reader.available > Limits.maxTotalHeaderBytes) {
                        // the parser checks the limit for complete headers only
                        throw HttpParseException("Total header size exceeds limit.", statusCode = 431)
                    }

                    continue
                }

                val headerLength = bytesRead + 1

                request.apply(context.server, context.endPoint, reader, context.remoteAddress, context.clientCertificate)

                if (request.hasBody && !reader.isCompleted && !body.isComplete(into, reader, headerLength)) {
                    // receiving more data may move the buffered header, so it is parsed again
                    into.clear()
                    pending = true
                    continue
                }

                pending = false
                body.reset()

                // the request header stays valid in the buffer until the body is read
                reader.consume(headerLength)

                val status = handleRequest(request)

                if (context.connection.isClosed || status == Connection.Upgrade || status == Connection.Close) {
                    // the remaining data is sent when the connection ends
                    return
                }

                request.drain()
                request.reset()

                if (context.outbound.backlog > HighWatermark) {
                    // the client does not read its responses, so its requests are not handled until it does
                    context.writer.flush()
                    context.outbound.flush()
                }

                if (reader.available > 0) {
                    // pipeline mode (more data available)
                    dataRemaining = true
                } else {
                    if (reader.isCompleted) {
                        break
                    }

                    if (reader.capacity > BufferSize.Read) {
                        // the reader grew for a large request: do not hold its memory while the connection is idle
                        // (the request references the buffer as well)
                        context.reader = context.newReader()
                        context.request = HttpRequest()

                        request = context.request
                        into = request.source
                    }
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
        }
    }

    // inlined, so that requests do not allocate a continuation
    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun handleRequest(request: HttpRequest): Connection {
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

        val keepAlive = keepAliveRequested && !closeRequested

        if (upgradeResponse) {
            // the content may read the upgraded connection blocking, so it runs on a virtual thread (the websockets
            // module reads it on the reactor instead, see SuspendingInput)
            withContext(UpgradeDispatcher) {
                context.responseHandler.handle(request, response, HttpProtocol.Http11, keepAlive, headRequest)
            }

            return Connection.Upgrade
        }

        val active = context.responseHandler.handle(request, response, HttpProtocol.Http11, keepAlive, headRequest)

        return if (active && keepAlive) Connection.KeepAlive else Connection.Close
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

    private fun logger(): Logger = context.server.logging.getLogger(Http1Driver::class.java.name)

    // endregion

    private companion object {

        val KeepAliveValue = ByteString("keep-alive")

        val Limits: ParserLimits = ParserLimits.Default

        /**
         * The number of bytes a connection may have written but not sent before it stops handling requests.
         */
        const val HighWatermark = 64 * 1024

        /**
         * Runs the content of upgraded connections, which may block (e.g. a tunnel reading the upgraded stream).
         */
        val UpgradeDispatcher: CoroutineDispatcher =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("genhttp-ioxide-upgrade-", 0).factory()).asCoroutineDispatcher()

    }

}
