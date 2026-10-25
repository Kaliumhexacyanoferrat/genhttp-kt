package org.genhttp.engine.ioxide.security

import ioxide.RecvItem
import ioxide.TcpConnection

import org.genhttp.engine.ioxide.io.OutboundChannel

import java.nio.ByteBuffer
import java.security.cert.X509Certificate

/**
 * The TLS session of a connection, once the handshake has been completed: decrypts what the connection receives and
 * encrypts what it writes (on the reactor thread).
 */
internal interface TlsTransport {

    /**
     * The certificate the client authenticated with, if any.
     */
    val clientCertificate: X509Certificate?

    /**
     * Takes the received bytes of the given item, starting at the given offset (the item is handed back afterward).
     */
    fun receive(item: RecvItem, offset: Int)

    /**
     * Decrypts the records that have been received completely.
     */
    fun decrypt()

    /**
     * The number of decrypted bytes that have not been read yet.
     */
    val available: Int

    /**
     * Copies decrypted bytes into the given buffer.
     *
     * @return The number of bytes copied
     */
    fun read(dst: ByteBuffer): Int

    /**
     * Whether the client ended the session (close notification).
     */
    val closed: Boolean

    /**
     * Whether the kernel encrypts the records sent (kTLS TX).
     */
    val kernelTx: Boolean

    /**
     * Encrypts the content of the given buffer and passes the records to the outbound channel, which stages them in
     * the slab of the connection or queues them while a send is in flight.
     */
    fun encrypt(plaintext: ByteBuffer, outbound: OutboundChannel)

    /**
     * Stages an entry queued by [encrypt] while a send was in flight in the slab of the connection.
     */
    fun stageQueued(entry: ByteBuffer, connection: TcpConnection)

    /**
     * Ends the session (sends the close notification if the connection is still open) and releases its resources.
     */
    fun close(outbound: OutboundChannel)

}
