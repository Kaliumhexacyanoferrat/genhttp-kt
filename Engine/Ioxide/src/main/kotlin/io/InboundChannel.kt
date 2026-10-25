package org.genhttp.engine.ioxide.io

import ioxide.RecvItem
import ioxide.RecvSnapshot
import ioxide.TcpConnection

import org.genhttp.engine.ioxide.security.TlsTransport

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * The data received by a connection, as the channel its `ConnectionReader` reads from (on the reactor thread).
 *
 * The connection waits for data via [await], which suspends until the ring delivers it, and copies it into its
 * reader via [read]. The buffers the kernel received into are handed back to the reactor as soon as they have been
 * copied (or decrypted), so a connection whose handler waits never holds one. Reads never wait: the connection only
 * reads after [await] reported data (or the end of the input).
 */
internal class InboundChannel : ReadableByteChannel {

    private var _connection: TcpConnection? = null

    private val connection: TcpConnection
        get() = _connection ?: throw IllegalStateException("The inbound channel is not bound to a connection")

    private var snapshot: RecvSnapshot? = null

    private val item = RecvItem()

    // an item taken from the queue whose bytes have not been copied completely
    private var holding = false

    private var offset = 0

    // the connection reported that nothing more will be received
    private var ended = false

    /**
     * The TLS session of the connection, which decrypts the received data, if any.
     */
    var tls: TlsTransport? = null

    // region Get-/Setters

    /**
     * Whether there is data that has not been read yet.
     */
    val hasData: Boolean
        get() {
            val tls = tls

            if (tls != null) {
                return tls.available > 0
            }

            return holding || queued() > 0
        }

    /**
     * Whether the end of the input has been reached (the client closed the connection, or ended the TLS session).
     */
    val isCompleted: Boolean
        get() = (ended || tls?.closed == true) && !hasData

    // endregion

    // region Lifecycle

    /**
     * Binds the instance to the given connection (on the reactor thread).
     */
    fun bind(connection: TcpConnection) {
        _connection = connection
    }

    /**
     * Hands back the buffer still held and resets the state, so that the instance can be bound to another connection
     * (on the reactor thread). Buffers still queued are handed back by the connection itself.
     */
    fun reset() {
        if (holding) {
            connection.returnBuffer(item)
            holding = false
        }

        offset = 0
        snapshot = null
        ended = false
        tls = null
        _connection = null
    }

    // endregion

    // region Functionality

    /**
     * Checks whether the connection needs to wait for data before it can read.
     */
    fun mustWait(): Boolean = !hasData && !isCompleted

    /**
     * Suspends until data has been received or the end of the input has been reached (on the reactor thread).
     *
     * The wait is not cancellable: connections end by being closed, which completes the input.
     */
    suspend fun await() {
        while (true) {
            decrypt()

            if (hasData || isCompleted) {
                return
            }

            if (ended) {
                return
            }

            val received = connection.read()

            snapshot = received

            if (received.isClosed) {
                ended = true
            }
        }
    }

    /**
     * Waits for the next data received from the client without interpreting it (for the TLS handshake, on the
     * reactor thread).
     *
     * @return false, if the connection has been closed and nothing is left to be read
     */
    suspend fun awaitReceived(): Boolean {
        while (true) {
            if (holding || queued() > 0) {
                return true
            }

            if (ended) {
                return false
            }

            val received = connection.read()

            snapshot = received

            if (received.isClosed) {
                ended = true
            }
        }
    }

    /**
     * Passes the received data to the given TLS session, which decrypts it (or keeps it for the handshake), handing
     * every buffer back afterward (on the reactor thread).
     */
    fun drainInto(tls: TlsTransport) {
        while (holding || take()) {
            tls.receive(item, offset)

            connection.returnBuffer(item)
            holding = false
            offset = 0
        }
    }

    override fun read(dst: ByteBuffer): Int {
        val tls = tls

        if (tls != null) {
            if (tls.available == 0) {
                return noData()
            }

            return tls.read(dst)
        }

        if (!holding && !take()) {
            return noData()
        }

        var total = 0

        while (dst.hasRemaining() && (holding || take())) {
            val count = minOf(item.length - offset, dst.remaining())

            if (dst.hasArray()) {
                item.copyTo(dst.array(), dst.arrayOffset() + dst.position(), offset, count)
                dst.position(dst.position() + count)
            } else {
                dst.put(item.segment().asSlice(offset.toLong(), count.toLong()).asByteBuffer())
            }

            offset += count
            total += count

            if (offset == item.length) {
                connection.returnBuffer(item)
                holding = false
                offset = 0
            }
        }

        return total
    }

    private fun noData(): Int {
        if (isCompleted) {
            return -1
        }

        // the connection only reads data it has been notified about
        throw IOException("No data has been received from the client yet")
    }

    // received buffers are decrypted as soon as they arrive, so their memory goes back to the reactor
    private fun decrypt() {
        val tls = tls ?: return

        drainInto(tls)
        tls.decrypt()
    }

    private fun queued(): Int {
        val current = snapshot ?: return 0
        return connection.itemCount(current)
    }

    private fun take(): Boolean {
        val current = snapshot ?: return false

        while (connection.tryGetItem(current, item)) {
            if (!item.hasBuffer) {
                continue
            }

            if (item.length == 0) {
                connection.returnBuffer(item)
                continue
            }

            holding = true
            offset = 0

            return true
        }

        return false
    }

    override fun isOpen(): Boolean = !isCompleted

    override fun close() {
        // owned by the connection
    }

    // endregion

}
