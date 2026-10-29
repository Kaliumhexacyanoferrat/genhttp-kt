package org.genhttp.engine.ioxide.security

import ioxide.RecvItem
import ioxide.TcpConnection

import org.genhttp.engine.ioxide.io.InboundChannel
import org.genhttp.engine.ioxide.io.OutboundChannel

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * TLS terminated by the JDK: an [SSLEngine] decrypts the data received by the connection and encrypts the records it
 * sends straight into its slab (on the reactor thread).
 */
internal class JdkTls(private val engine: SSLEngine, private val outbound: OutboundChannel) : TlsTransport {

    /** Encrypted data received from the client (write mode). */
    private var netIn: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)

    /** Decrypted application data (read mode). */
    private var appIn: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize).flip()

    private var ended = false

    override var clientCertificate: X509Certificate? = null
        private set

    // region Handshake

    /**
     * Performs the TLS handshake with the client.
     *
     * @return false, if the connection has been closed before the handshake completed
     */
    suspend fun handshake(inbound: InboundChannel): Boolean {
        engine.beginHandshake()

        while (true) {
            when (engine.handshakeStatus) {
                // the records are sent once the reactor has handled the current completions, or before waiting below
                HandshakeStatus.NEED_WRAP -> wrap(EMPTY)

                HandshakeStatus.NEED_UNWRAP, HandshakeStatus.NEED_UNWRAP_AGAIN -> {
                    val result = unwrap()

                    if (result == null) {
                        if (!inbound.awaitReceived()) {
                            return false
                        }

                        inbound.drainInto(this)
                    } else if (result.status == Status.CLOSED) {
                        return false
                    }
                }

                HandshakeStatus.NEED_TASK -> runTasks()

                else -> break
            }
        }

        clientCertificate = try {
            engine.session.peerCertificates.firstOrNull() as? X509Certificate
        } catch (_: SSLPeerUnverifiedException) {
            null
        }

        // application data the client sent along with its last handshake message
        decrypt()

        return true
    }

    /**
     * Sends the alert the engine produced for a failed handshake, if any.
     */
    fun abort() {
        try {
            engine.closeOutbound()

            if (!engine.isOutboundDone) {
                wrapRecord(EMPTY)
            }
        } catch (_: Exception) {
            // nothing left to tell the client
        }
    }

    // endregion

    // region Receiving

    override fun receive(item: RecvItem, offset: Int) {
        val count = item.length - offset

        if (netIn.remaining() < count) {
            netIn = ByteBuffer.allocate(maxOf(netIn.capacity() * 2, netIn.position() + count)).put(netIn.flip())
        }

        item.copyTo(netIn.array(), netIn.arrayOffset() + netIn.position(), offset, count)
        netIn.position(netIn.position() + count)
    }

    override fun decrypt() {
        while (!ended && netIn.position() > 0) {
            val result = unwrap() ?: return

            if (result.status == Status.CLOSED) {
                ended = true
                return
            }

            processHandshakeStatus(result.handshakeStatus)

            if (result.bytesConsumed() == 0 && result.bytesProduced() == 0) {
                return
            }
        }
    }

    override val available: Int
        get() = appIn.remaining()

    override fun read(dst: ByteBuffer): Int {
        val count = minOf(dst.remaining(), appIn.remaining())

        dst.put(dst.position(), appIn, appIn.position(), count)
        dst.position(dst.position() + count)
        appIn.position(appIn.position() + count)

        return count
    }

    override val closed: Boolean
        get() = ended

    override val kernelTx: Boolean
        get() = false

    /**
     * Unwraps the next record.
     *
     * @return The result, or null if the record has not been received completely yet
     */
    private fun unwrap(): SSLEngineResult? {
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
                    }

                    return null
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

    // region Sending

    override fun encrypt(plaintext: ByteBuffer, outbound: OutboundChannel) {
        wrap(plaintext)
    }

    override fun stageQueued(entry: ByteBuffer, connection: TcpConnection) {
        // queued as records
        connection.write(entry.array(), entry.arrayOffset() + entry.position(), entry.remaining())
    }

    private fun wrap(source: ByteBuffer) {
        do {
            val result = wrapRecord(source)

            if (result.status == Status.CLOSED && source.hasRemaining()) {
                throw ClosedChannelException()
            }

            if (result.handshakeStatus == HandshakeStatus.NEED_TASK) {
                runTasks()
            }
        } while (source.hasRemaining())
    }

    // encrypts the next record into the slab (or a queued buffer while a send is in flight)
    private fun wrapRecord(source: ByteBuffer): SSLEngineResult {
        val target = outbound.recordTarget(engine.session.packetBufferSize)

        val result = engine.wrap(source, target)

        outbound.recordWritten(target, result.bytesProduced())

        if (result.status == Status.BUFFER_OVERFLOW) {
            throw IllegalStateException("The TLS record does not fit into ${target.capacity()} bytes")
        }

        return result
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

                HandshakeStatus.NEED_WRAP -> {
                    wrapRecord(EMPTY)
                    engine.handshakeStatus
                }

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

    override fun close(outbound: OutboundChannel) {
        if (!outbound.isOpen) {
            return
        }

        try {
            engine.closeOutbound()

            var records = 0

            while (!engine.isOutboundDone && records++ < MaxCloseRecords) {
                wrapRecord(EMPTY)
            }
        } catch (_: Exception) {
            // the client may already be gone
        }
    }

    // endregion

    private companion object {

        val EMPTY: ByteBuffer = ByteBuffer.allocate(0)

        const val MaxCloseRecords = 4

    }

}
