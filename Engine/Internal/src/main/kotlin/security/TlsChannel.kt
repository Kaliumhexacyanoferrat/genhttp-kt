package org.genhttp.engine.internal.security

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.SocketChannel
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status

/**
 * A byte channel that encrypts the traffic of a (blocking) socket channel using an [SSLEngine].
 *
 * Reads and writes may happen concurrently from two threads (e.g. for upgraded connections), as the
 * engine synchronizes wrapping and unwrapping independently and both directions use their own buffers.
 */
internal class TlsChannel(private val channel: SocketChannel, private val engine: SSLEngine) : ByteChannel {

    private val readLock = Any()

    private val writeLock = Any()

    /** Encrypted data received from the network (write mode). */
    private var netIn: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)

    /** Encrypted data to be sent to the network. */
    private var netOut: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)

    /** Decrypted application data (read mode). */
    private var appIn: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize).flip()

    @Volatile
    private var closed = false

    // region Handshake

    /**
     * Performs the TLS handshake with the client.
     */
    fun handshake() {
        engine.beginHandshake()

        var status = engine.handshakeStatus

        while (status != HandshakeStatus.FINISHED && status != HandshakeStatus.NOT_HANDSHAKING) {
            status = when (status) {
                HandshakeStatus.NEED_WRAP -> synchronized(writeLock) { wrap(EMPTY).handshakeStatus }

                HandshakeStatus.NEED_UNWRAP, HandshakeStatus.NEED_UNWRAP_AGAIN -> synchronized(readLock) {
                    val result = unwrap()

                    if (result.status == Status.CLOSED) {
                        throw EOFException("Connection closed during the TLS handshake")
                    }

                    result.handshakeStatus
                }

                HandshakeStatus.NEED_TASK -> {
                    runTasks()
                    engine.handshakeStatus
                }

                else -> engine.handshakeStatus
            }
        }
    }

    // endregion

    // region Reading

    override fun read(dst: ByteBuffer): Int = synchronized(readLock) {
        if (appIn.hasRemaining()) {
            return copy(dst)
        }

        while (true) {
            val result = try {
                unwrap()
            } catch (_: EOFException) {
                return -1
            }

            if (result.status == Status.CLOSED) {
                return -1
            }

            processHandshakeStatus(result.handshakeStatus)

            if (appIn.hasRemaining()) {
                return copy(dst)
            }
        }

        @Suppress("UNREACHABLE_CODE")
        -1
    }

    private fun copy(dst: ByteBuffer): Int {
        val count = minOf(dst.remaining(), appIn.remaining())

        val slice = appIn.slice(appIn.position(), count)
        dst.put(slice)
        appIn.position(appIn.position() + count)

        return count
    }

    /**
     * Unwraps the next record, reading from the network if needed.
     *
     * @throws EOFException if the network stream ended before a record could be read
     */
    private fun unwrap(): SSLEngineResult {
        while (true) {
            netIn.flip()
            appIn.compact()

            val result = try {
                engine.unwrap(netIn, appIn)
            } finally {
                netIn.compact()
                appIn.flip()
            }

            when (result.status) {
                Status.BUFFER_UNDERFLOW -> {
                    val required = engine.session.packetBufferSize

                    if (netIn.capacity() < required) {
                        netIn = ByteBuffer.allocate(required).put(netIn.flip())
                    } else if (!netIn.hasRemaining()) {
                        netIn = ByteBuffer.allocate(netIn.capacity() * 2).put(netIn.flip())
                    }

                    if (channel.read(netIn) < 0) {
                        throw EOFException("Connection closed by peer")
                    }
                }

                Status.BUFFER_OVERFLOW -> {
                    val required = engine.session.applicationBufferSize + appIn.remaining()
                    val grown = ByteBuffer.allocate(maxOf(required, appIn.capacity() * 2))

                    grown.put(appIn)
                    appIn = grown.flip()
                }

                else -> return result
            }
        }
    }

    // endregion

    // region Writing

    override fun write(src: ByteBuffer): Int = synchronized(writeLock) {
        if (closed) {
            throw ClosedChannelException()
        }

        val count = src.remaining()

        while (src.hasRemaining()) {
            val result = wrap(src)

            if (result.status == Status.CLOSED) {
                throw ClosedChannelException()
            }

            if (result.handshakeStatus == HandshakeStatus.NEED_TASK) {
                runTasks()
            }
        }

        count
    }

    private fun wrap(src: ByteBuffer): SSLEngineResult {
        while (true) {
            netOut.clear()

            val result = engine.wrap(src, netOut)

            when (result.status) {
                Status.BUFFER_OVERFLOW -> {
                    netOut = ByteBuffer.allocate(maxOf(engine.session.packetBufferSize, netOut.capacity() * 2))
                }

                else -> {
                    netOut.flip()

                    while (netOut.hasRemaining()) {
                        channel.write(netOut)
                    }

                    return result
                }
            }
        }
    }

    // endregion

    // region Helpers

    private fun processHandshakeStatus(initial: HandshakeStatus) {
        var status = initial

        while (true) {
            status = when (status) {
                HandshakeStatus.NEED_TASK -> {
                    runTasks()
                    engine.handshakeStatus
                }

                HandshakeStatus.NEED_WRAP -> synchronized(writeLock) { wrap(EMPTY).handshakeStatus }

                else -> return
            }
        }
    }

    private fun runTasks() {
        while (true) {
            val task = engine.delegatedTask ?: break
            task.run()
        }
    }

    // endregion

    // region Lifecycle

    override fun isOpen(): Boolean = channel.isOpen && !closed

    override fun close() {
        if (closed) {
            return
        }

        closed = true

        try {
            engine.closeOutbound()

            synchronized(writeLock) {
                while (!engine.isOutboundDone) {
                    wrap(EMPTY)
                }
            }
        } catch (_: Exception) {
            // the peer may already be gone
        } finally {
            channel.close()
        }
    }

    // endregion

    private companion object {

        val EMPTY: ByteBuffer = ByteBuffer.allocate(0)

    }

}
