package org.genhttp.modules.websockets.protocol

import org.genhttp.api.protocol.ByteString

import java.security.MessageDigest
import java.util.Base64

/**
 * Implements the computations required by the websocket opening handshake (RFC 6455).
 *
 * The websocket handshake is an HTTP-based negotiation that upgrades a normal HTTP connection into a persistent,
 * full-duplex websocket channel. The client sends a GET request with the headers `Upgrade: websocket`,
 * `Connection: Upgrade`, `Sec-WebSocket-Version: 13` and a randomly generated `Sec-WebSocket-Key`. The server
 * must append a fixed UUID to this key, compute the SHA-1 hash of the result and return the Base64 encoded value
 * in the `Sec-WebSocket-Accept` header together with the status `101 Switching Protocols`. After this response,
 * both sides stop speaking HTTP and exchange raw websocket frames over the same connection.
 */
public object Handshake {

    private val MagicString: ByteArray = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".toByteArray(Charsets.US_ASCII)

    /**
     * Computes the value of the `Sec-WebSocket-Accept` header for the given `Sec-WebSocket-Key`.
     *
     * @param key The key sent by the client
     * @return The key to be sent back to the client
     */
    public fun createAcceptKey(key: ByteString): ByteString {
        val digest = MessageDigest.getInstance("SHA-1")

        digest.update(key.bytes)
        digest.update(MagicString)

        return ByteString(Base64.getEncoder().encode(digest.digest()))
    }

}
