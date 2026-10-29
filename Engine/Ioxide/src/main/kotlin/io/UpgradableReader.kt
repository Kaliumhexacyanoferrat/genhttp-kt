package org.genhttp.engine.ioxide.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.genhttp.api.protocol.SuspendingInput
import org.genhttp.engine.ioxide.infrastructure.ReactorDispatcher
import org.genhttp.engine.shared.io.ConnectionReader

import java.io.InputStream

/**
 * The reader of a connection, which exposes the input of upgraded connections as a [SuspendingInput].
 */
internal class UpgradableReader(
    private val inbound: InboundChannel,
    private val outbound: OutboundChannel,
    private val dispatcher: ReactorDispatcher
) : ConnectionReader(inbound) {

    override fun asInputStream(): InputStream = UpgradedStream(this, inbound, outbound, dispatcher)

    /**
     * The input of an upgraded connection: suspending reads release the thread until the reactor delivers data,
     * blocking reads (e.g. on a virtual thread) wait for it on the calling thread. Suspending reads also wait while
     * the client does not read the data sent to it (writes on the reactor do not wait).
     */
    private class UpgradedStream(
        private val reader: ConnectionReader,
        private val inbound: InboundChannel,
        private val outbound: OutboundChannel,
        private val reactor: ReactorDispatcher
    ) : InputStream(), SuspendingInput {

        override val dispatcher: CoroutineDispatcher
            get() = reactor

        override fun read(): Int {
            val single = ByteArray(1)
            val read = read(single, 0, 1)
            return if (read < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) {
                return 0
            }

            if (reactor.reactor.onReactorThread) {
                if (reader.available == 0 && inbound.mustWait()) {
                    // the data could only be delivered by the reactor that would be blocked
                    throw IllegalStateException("Upgraded connections cannot be read blocking on their reactor, use SuspendingInput.readSuspending()")
                }

                return reader.read(b, off, len)
            }

            return runBlocking(reactor) { readSuspending(b, off, len) }
        }

        override fun available(): Int = reader.available

        override suspend fun readSuspending(target: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) {
                return 0
            }

            if (!reactor.reactor.onReactorThread) {
                return withContext(reactor) { readSuspending(target, offset, length) }
            }

            if (outbound.backlog > HighWatermark) {
                outbound.flush()
            }

            if (reader.available == 0 && inbound.mustWait()) {
                inbound.await()
            }

            // the data has been received, so the reader does not need to wait
            return reader.read(target, offset, length)
        }

        override fun close() {
            // owned by the connection
        }

    }

    private companion object {

        /**
         * The number of bytes an upgraded connection may have written but not sent before it stops reading.
         */
        const val HighWatermark = 64 * 1024

    }

}
