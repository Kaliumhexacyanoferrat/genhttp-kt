package org.genhttp.engine.ioxide.security

import ioxide.Memory
import ioxide.RecvItem
import ioxide.TcpConnection
import ioxide.tls.TlsSession

import org.genhttp.engine.ioxide.io.OutboundChannel

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.security.cert.X509Certificate

/**
 * TLS terminated by BoringSSL through ioxide: the session of the connection decrypts the data it receives (or the
 * kernel does, with kTLS RX) and encrypts the data it writes into its slab (or the kernel does, with kTLS TX).
 */
internal class BoringSslTls(private val session: TlsSession) : TlsTransport {

    /** Decrypted application data (read mode). */
    private var plain: ByteBuffer = ByteBuffer.allocate(InitialCapacity).flip()

    init {
        // application data that rode in with the last flight of the handshake
        append(session.drainPlaintext())
    }

    override val clientCertificate: X509Certificate?
        get() = null

    // region Receiving

    override fun receive(item: RecvItem, offset: Int) {
        // the plaintext is only valid until the next call, so it is copied right away
        append(session.decrypt(item.address + offset, item.length - offset))
    }

    override fun decrypt() {
        // decrypted as received
    }

    private fun append(count: Int) {
        if (count <= 0) {
            return
        }

        plain.compact()

        if (plain.remaining() < count) {
            plain = ByteBuffer.allocate(maxOf(plain.capacity() * 2, plain.position() + count)).put(plain.flip())
        }

        Memory.copy(session.plaintext, plain.array(), plain.arrayOffset() + plain.position(), count)

        plain.position(plain.position() + count)
        plain.flip()
    }

    override val available: Int
        get() = plain.remaining()

    override fun read(dst: ByteBuffer): Int {
        val count = minOf(dst.remaining(), plain.remaining())

        dst.put(dst.position(), plain, plain.position(), count)
        dst.position(dst.position() + count)
        plain.position(plain.position() + count)

        return count
    }

    override val closed: Boolean
        get() = session.closed

    override val kernelTx: Boolean
        get() = session.kernelTx

    // endregion

    // region Sending

    override fun encrypt(plaintext: ByteBuffer, outbound: OutboundChannel) {
        if (outbound.canStage) {
            write(plaintext, outbound.connection)
        } else {
            // encrypted once the send in flight has completed, the records leave in the order they are produced
            val copy = ByteBuffer.allocate(plaintext.remaining())

            copy.put(plaintext).flip()
            outbound.enqueue(copy)
        }
    }

    override fun stageQueued(entry: ByteBuffer, connection: TcpConnection) {
        write(entry, connection)
    }

    private fun write(plaintext: ByteBuffer, connection: TcpConnection) {
        val count = plaintext.remaining()

        if (plaintext.hasArray()) {
            session.write(connection, plaintext.array(), plaintext.arrayOffset() + plaintext.position(), count)
        } else {
            session.write(connection, MemorySegment.ofBuffer(plaintext).address(), count)
        }

        plaintext.position(plaintext.limit())
    }

    // endregion

    // region Lifecycle

    override fun close(outbound: OutboundChannel) {
        // sends the close notification if it can still go out cleanly and frees the session
        session.close()
    }

    // endregion

    private companion object {

        const val InitialCapacity = 16 * 1024

    }

}
