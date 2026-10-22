package org.genhttp.benchmarks.infrastructure

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.internal.context.ConnectionContext
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

/**
 * Runs a single request through the request pipeline of the engine (parser, handler, response serialization)
 * without any network involved.
 *
 * @param request The raw HTTP request to be processed on every execution
 * @param handler The handler to serve the request with
 */
class BenchmarkContext(request: String, handler: Handler) {

    private val request = request.toByteArray(Charsets.US_ASCII)

    private val channel = MemoryChannel()

    private val server = BenchmarkServer(handler)

    private val context = ConnectionContext(server, BenchmarkEndPoint, channel, channel, null, InetAddress.getLoopbackAddress())

    suspend fun prepare() {
        server.handler.prepare(server)
    }

    suspend fun execute() {
        channel.reset(request)

        val reader = context.reader

        reader.fill()

        val bytesRead = UltraHardenedParser.tryExtractFullHeader(reader.data(), context.request.source, Limits)

        require(bytesRead >= 0) { "Given request could not be parsed." }

        reader.consume(bytesRead + 1)

        context.request.apply(server, BenchmarkEndPoint, reader, context.remoteAddress, null)

        context.clientHandler.handleRequest(context.request)
        context.writer.flush()

        context.request.drain()
        context.request.reset()

        reader.consume(reader.available)
    }

    private companion object {

        val Limits: ParserLimits = ParserLimits.Default

    }

    /**
     * Provides the request on reads and discards everything written to it.
     */
    private class MemoryChannel : ByteChannel {

        private var input: ByteBuffer = ByteBuffer.allocate(0)

        fun reset(data: ByteArray) {
            input = ByteBuffer.wrap(data)
        }

        override fun read(dst: ByteBuffer): Int {
            if (!input.hasRemaining()) {
                return -1
            }

            val count = minOf(dst.remaining(), input.remaining())

            dst.put(input.slice(input.position(), count))
            input.position(input.position() + count)

            return count
        }

        override fun write(src: ByteBuffer): Int {
            val count = src.remaining()
            src.position(src.limit())
            return count
        }

        override fun isOpen(): Boolean = true

        override fun close() {
        }

    }

    private object BenchmarkEndPoint : EndPoint {

        override val address: InetAddress? = InetAddress.getLoopbackAddress()

        override val port: Int = 8080

        override val protocols: HttpProtocols = HttpProtocols.Http1

        override val dualStack: Boolean = false

        override val secure: Boolean = false

        override fun close() {
        }

    }

}
