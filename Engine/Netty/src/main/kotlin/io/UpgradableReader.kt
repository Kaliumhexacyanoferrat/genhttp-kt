package org.genhttp.engine.netty.io

import kotlinx.coroutines.CoroutineDispatcher

import org.genhttp.api.protocol.SuspendingInput
import org.genhttp.engine.shared.io.ConnectionReader

import java.io.InputStream

/**
 * The reader of a connection, which exposes the input of upgraded connections as a [SuspendingInput].
 *
 * @param dispatcher Provides the dispatcher of the event loop of the connection
 */
internal class UpgradableReader(
    private val inbound: InboundChannel,
    private val outbound: OutboundChannel,
    private val dispatcher: () -> CoroutineDispatcher
) : ConnectionReader(inbound) {

    override fun asInputStream(): InputStream = UpgradedStream(this, inbound, outbound, dispatcher())

    /**
     * The input of an upgraded connection: blocking reads wait for the data on the calling thread (e.g. a virtual
     * thread), suspending reads release the thread until the event loop delivers data. Suspending reads also wait
     * while the client does not read the data sent to it (writes on the event loop do not wait).
     */
    private class UpgradedStream(
        private val reader: ConnectionReader,
        private val inbound: InboundChannel,
        private val outbound: OutboundChannel,
        override val dispatcher: CoroutineDispatcher
    ) : InputStream(), SuspendingInput {

        override fun read(): Int {
            val single = ByteArray(1)
            val read = read(single, 0, 1)
            return if (read < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = reader.read(b, off, len)

        override fun available(): Int = reader.available

        override suspend fun readSuspending(target: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) {
                return 0
            }

            outbound.awaitWritable()

            if (reader.available == 0) {
                inbound.awaitUpgraded()
            }

            // the data has been received, so the reader does not need to wait
            return reader.read(target, offset, length)
        }

        override fun close() {
            // owned by the connection
        }

    }

}
