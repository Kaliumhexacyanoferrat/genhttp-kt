package org.genhttp.modules.reverseproxy.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

import kotlin.coroutines.coroutineContext

/**
 * Passes the bytes of an upgraded client connection to an upstream server and vice versa.
 *
 * @param rawWebsocketConnection The connection to the upstream server (already upgraded)
 * @param request The request that initiated the websocket connection (grants access to the connection of the client)
 */
public class WebsocketTunnelContent(
    private val rawWebsocketConnection: RawWebsocketConnection,
    private val request: Request
) : ResponseContent, AutoCloseable {

    override val length: Long? = null

    override val type: ContentType? = null

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = null

    override suspend fun write(sink: ResponseSink) {
        sink.stream.flush()

        val upstreamPipe = rawWebsocketConnection.pipe
            ?: throw IllegalStateException("Not initialized.")

        val downstreamInput = request.upgrade()

        try {
            coroutineScope {
                val toDownstream = launchPump(upstreamPipe.input, sink.stream)
                val toUpstream = launchPump(downstreamInput, upstreamPipe.output)

                // if either direction ends, the connection is done
                select {
                    toDownstream.onJoin { }
                    toUpstream.onJoin { }
                }

                // unblocks the pump reading from the upstream
                rawWebsocketConnection.close()

                toDownstream.cancel()
                toUpstream.cancel()
            }
        } finally {
            // Always run: dispose the connection, this will close the upstream pipe
            rawWebsocketConnection.close()
        }
    }

    override fun close() {
        rawWebsocketConnection.close()
    }

    private fun CoroutineScope.launchPump(source: InputStream, target: OutputStream) = launch {
        val buffer = ByteArray(BufferSize)

        try {
            while (true) {
                val read = runInterruptible { source.read(buffer) }

                if (read < 0) {
                    break
                }

                coroutineContext.ensureActive()

                target.write(buffer, 0, read)
                target.flush()
            }
        } catch (_: IOException) {
            // one of the peers went away, which ends the tunnel
        }
    }

    private companion object {

        private const val BufferSize = 4096 * 4

    }

}
