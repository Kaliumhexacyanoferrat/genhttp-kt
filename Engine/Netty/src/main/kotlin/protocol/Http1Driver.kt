package org.genhttp.engine.netty.protocol

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
import org.genhttp.engine.netty.context.ConnectionContext
import org.genhttp.engine.shared.types.ConnectionExceptions
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.HttpResponseBuilder
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser
import org.genhttp.modules.io.strings.StringContent

import org.slf4j.Logger

import java.util.concurrent.Executors

import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Serves HTTP/1.1 on one connection, request after request (`Http1Driver` of the ioxide engine in GenHTTP).
 *
 * The driver runs as a coroutine on the event loop of the connection. It waits for data (suspending until the loop
 * delivers it), parses the request header, waits until the body has been received completely, passes the request
 * to the handler chain and writes the response. Pipelined requests are handled without waiting, their responses are
 * sent with a single flush.
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
        // only resolved if an actual error needs to be logged, so well behaved connections never pay for it
        var logger: Logger? = null

        fun logUnlessGraceful(e: Throwable, message: String) {
            if (ConnectionExceptions.isGracefulDisconnect(e)) {
                return
            }

            val current = logger ?: context.server.logging.getLogger(Http1Driver::class.java.name).also { logger = it }
            current.warn(message, e)
        }

        try {
            handleConnection()
        } catch (e: Throwable) {
            logUnlessGraceful(e, "Failed to handle client connection")
        } finally {
            try {
                context.outbound.closeWhenSent()
            } catch (e: Exception) {
                logUnlessGraceful(e, "Failed to shut down client connection")
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
                        // passes the responses to the channel before waiting, which flushes them when the read
                        // completes (so pipelined requests received at once are answered with a single flush)
                        context.writer.flush()

                        // suspends without allocating a continuation for the wait (resumed inline by the loop)
                        suspendCoroutineUninterceptedOrReturn { continuation ->
                            inbound.park(continuation.intercepted())
                            COROUTINE_SUSPENDED
                        }
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

                request.apply(context.server, context.endPoint, reader, context.remoteAddress, context.clientCertificate, context.writer)

                var deferred = false

                if (request.hasBody && !reader.isCompleted) {
                    if (expectsContinue(request)) {
                        // the client waits for a 100 (Continue) before it sends the body, so the body is read as
                        // it arrives instead of being buffered before the handler is invoked
                        deferred = true
                    } else if (!body.isComplete(into, reader, headerLength)) {
                        // receiving more data may move the buffered header, so it is parsed again
                        into.clear()
                        pending = true
                        continue
                    }
                }

                pending = false
                body.reset()

                // the request header stays valid in the buffer until the body is read
                reader.consume(headerLength)

                val status = if (deferred) handleDeferred(request) else handleRequest(request)

                if (!context.channel.isActive) {
                    return
                }

                if (status == Connection.Upgrade) {
                    // the content has been written and flushed on the virtual thread
                    return
                }

                // deferred requests have been finished on their virtual thread already
                if (status == Connection.Close || (!deferred && !request.finish())) {
                    context.writer.flush()
                    return
                }

                request.reset()

                if (!context.channel.isWritable) {
                    // the client does not read its responses, so its requests are not handled until it does
                    context.writer.flush()
                    context.outbound.awaitWritable()
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
        } finally {
            context.writer.flush()
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
            // the content reads from the blocking stream of the upgraded connection, so it runs on a virtual thread
            context.inbound.upgrade()

            withContext(UpgradeDispatcher) {
                context.responseHandler.handle(request, response, HttpProtocol.Http11, keepAlive, headRequest)
            }

            return Connection.Upgrade
        }

        val active = context.responseHandler.handle(request, response, HttpProtocol.Http11, keepAlive, headRequest)

        return if (active && keepAlive) Connection.KeepAlive else Connection.Close
    }

    /**
     * Handles a request whose client waits for a 100 (Continue) before it sends the body.
     *
     * The body is read from the blocking stream of the request as it arrives, so the request is handled and finished
     * on a virtual thread while the event loop delivers the data, like the content of an upgraded connection.
     */
    private suspend fun handleDeferred(request: HttpRequest): Connection {
        val inbound = context.inbound

        inbound.block()

        var status = Connection.Close

        try {
            status = withContext(UpgradeDispatcher) {
                val handled = handleRequest(request)

                // the client sends the body after the 100 or the final response, so skipping it may wait for data
                if (handled == Connection.KeepAlive && !request.finish()) Connection.Close else handled
            }

            return status
        } finally {
            if (status != Connection.Upgrade) {
                inbound.unblock()
            }
        }
    }

    /**
     * Checks whether the client waits for a 100 (Continue) before sending the body (ignored for HTTP/1.0 clients,
     * RFC 9110, section 10.1.1).
     */
    private fun expectsContinue(request: HttpRequest): Boolean {
        val header = request.header

        if (header.protocol == HttpProtocol.Http10) {
            return false
        }

        return header.headers.getEntry(KnownHeaders.Expect) == ContinueValue
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

        val KeepAliveValue = ByteString("keep-alive")

        val ContinueValue = ByteString("100-continue")

        val Limits: ParserLimits = ParserLimits.Default

        /**
         * Runs the content of upgraded connections, which may block (e.g. websockets reading frames), and requests
         * whose body is read as it arrives.
         */
        val UpgradeDispatcher: CoroutineDispatcher =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("genhttp-netty-upgrade-", 0).factory()).asCoroutineDispatcher()

    }

}
