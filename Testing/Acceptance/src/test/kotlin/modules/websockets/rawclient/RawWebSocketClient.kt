package org.genhttp.testing.acceptance.modules.websockets.rawclient

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64

/**
 * A frame received by the raw client.
 */
data class RawFrame(val opcode: Byte, val fin: Boolean, val payload: ByteArray)

/**
 * A minimal websocket client working on a plain socket, allowing to send frames in arbitrary chunks.
 *
 * Reads are bound by [readTimeoutMillis] as there are no cancellation tokens to bound them otherwise.
 */
class RawWebSocketClient(private val readTimeoutMillis: Int = 30000) : AutoCloseable {

    private val socket: Socket = Socket().apply { tcpNoDelay = true }

    private lateinit var input: InputStream

    private lateinit var output: OutputStream

    fun connect(host: String, port: Int) {
        socket.connect(InetSocketAddress(host, port))
        socket.soTimeout = readTimeoutMillis

        input = socket.getInputStream()
        output = socket.getOutputStream()

        // Handshake
        val keyBytes = ByteArray(16)
        random.nextBytes(keyBytes)
        val secWebSocketKey = Base64.getEncoder().encodeToString(keyBytes)

        val request =
            "GET / HTTP/1.1\r\n" +
                    "Host: $host:$port\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $secWebSocketKey\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "\r\n"

        output.write(request.toByteArray(Charsets.US_ASCII))
        output.flush()

        // Read handshake response
        val buffer = ByteArray(4096)
        var received = 0

        while (true) {
            val read = input.read(buffer, received, buffer.size - received)

            if (read <= 0) {
                throw IllegalStateException("Server closed connection during handshake.")
            }

            received += read

            val headerText = String(buffer, 0, received, Charsets.US_ASCII)

            if (headerText.contains("\r\n\r\n")) {
                break
            }
        }

        // Validate "HTTP/1.1 101" and Sec-WebSocket-Accept here?
    }

    /**
     * Send raw bytes split into TCP chunks.
     */
    fun sendRawInChunks(data: ByteArray, chunkSize: Int) {
        if (chunkSize <= 0) {
            throw IllegalArgumentException("chunkSize")
        }

        var offset = 0

        while (offset < data.size) {
            val toSend = minOf(chunkSize, data.size - offset)

            output.write(data, offset, toSend)
            output.flush()

            offset += toSend

            if (offset < data.size) {
                // Ensure each chunk arrives as a separate receive on every platform,
                // so the server is actually exercised with fragmented reads.
                Thread.sleep(1)
            }
        }
    }

    /**
     * Send a *single* WebSocket text frame,
     * but split the frame's bytes across multiple Socket.Send calls.
     */
    fun sendTextInTcpChunks(text: String, chunkSize: Int) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val frame = buildClientFrame(payload, opcode = 0x1, fin = true)

        sendRawInChunks(frame, chunkSize)
    }

    /**
     * Send multiple complete WebSocket text frames in a *single* Socket.Send call. ("Pipelining" :) )
     */
    fun sendMultipleTextFramesSingleWrite(vararg messages: String) {
        if (messages.isEmpty()) {
            return
        }

        // Build all frames and concatenate into one buffer
        val buffer = messages
            .map { buildClientFrame(it.toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true) }
            .fold(ByteArray(0)) { acc, frame -> acc + frame }

        // Single write: the server will receive multiple WebSocket frames in one read.
        output.write(buffer)
        output.flush()
    }

    private fun receiveExact(buffer: ByteArray) {
        var totalRead = 0

        while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)

            if (read < 0) {
                throw IllegalStateException("Socket closed while receiving.")
            }

            totalRead += read
        }
    }

    /**
     * Receive a single WebSocket frame and return its text payload.
     * Assumes server echoes text frames (opcode=1) and uses FIN=1.
     * Handles unmasked (normal server) and masked (defensive) payloads.
     */
    fun receiveTextFrame(): String {
        // Read first 2 bytes (b0 + b1)
        val header = ByteArray(2)
        receiveExact(header)

        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF

        val fin = (b0 and 0x80) != 0
        val opcode = b0 and 0x0F

        if (!fin) {
            throw UnsupportedOperationException("Fragmented server frames not supported in this test helper.")
        }

        if (opcode != 0x1) {
            // Text only
            throw UnsupportedOperationException("Unexpected opcode $opcode in receiveTextFrame.")
        }

        return String(readPayload(b1), Charsets.UTF_8)
    }

    /**
     * Receive a single raw WebSocket frame (any opcode) and return its opcode, FIN bit and payload.
     */
    fun receiveFrame(): RawFrame {
        val header = ByteArray(2)
        receiveExact(header)

        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF

        val fin = (b0 and 0x80) != 0
        val opcode = (b0 and 0x0F).toByte()

        return RawFrame(opcode, fin, readPayload(b1))
    }

    private fun readPayload(b1: Int): ByteArray {
        val masked = (b1 and 0x80) != 0 // servers SHOULD NOT mask, but handle just in case
        val len7 = b1 and 0x7F

        var payloadLen = len7.toLong()

        if (len7 == 126) {
            val lenBytes = ByteArray(2)
            receiveExact(lenBytes)

            payloadLen = (((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)).toLong()
        } else if (len7 == 127) {
            val lenBytes = ByteArray(8)
            receiveExact(lenBytes)

            payloadLen = lenBytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        }

        var maskKey: ByteArray? = null

        if (masked) {
            maskKey = ByteArray(4)
            receiveExact(maskKey)
        }

        val payload = ByteArray(payloadLen.toInt())

        if (payloadLen > 0) {
            receiveExact(payload)
        }

        if (maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i and 0x03].toInt()).toByte()
            }
        }

        return payload
    }

    fun sendTextAsContinuationFramesInTcpChunks(text: String, wsFragmentPayloadSize: Int, tcpChunkSize: Int) {
        if (wsFragmentPayloadSize <= 0) throw IllegalArgumentException("wsFragmentPayloadSize")
        if (tcpChunkSize <= 0) throw IllegalArgumentException("tcpChunkSize")

        val bytes = text.toByteArray(Charsets.UTF_8)

        // Build: TEXT(fin=0) + CONT(fin=0)* + CONT(fin=1)
        // Then concatenate into one buffer so we can TCP-chunk it.
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        var fragIndex = 0

        while (offset < bytes.size) {
            val take = minOf(wsFragmentPayloadSize, bytes.size - offset)
            val isFirst = fragIndex == 0
            val isFinal = (offset + take) == bytes.size

            val opcode: Byte = if (isFirst) 0x1 /*Text*/ else 0x0 /*Continuation*/
            val fin = isFinal

            frames.add(buildClientFrame(bytes.copyOfRange(offset, offset + take), opcode, fin))

            offset += take
            fragIndex++
        }

        val buffer = frames.fold(ByteArray(0)) { acc, frame -> acc + frame }

        sendRawInChunks(buffer, tcpChunkSize)
    }

    /**
     * Half-closes the write side of the connection (sends FIN) while leaving the read side open.
     */
    fun shutdownWrite() = socket.shutdownOutput()

    override fun close() {
        try {
            socket.shutdownOutput()
        } catch (_: Exception) {
            // ignore
        }

        socket.close()
    }

    companion object {

        private val random = SecureRandom()

        /**
         * Build a single client->server WebSocket frame (masked as required for clients).
         */
        fun buildClientFrame(payload: ByteArray, opcode: Byte, fin: Boolean): ByteArray {
            val maskBit = 0x80

            var b0 = 0

            if (fin) {
                b0 = b0 or 0x80
            }

            b0 = b0 or (opcode.toInt() and 0x0F) // lower 4 bits

            val payloadLen = payload.size

            val headerLen = when {
                payloadLen <= 125 -> 2 + 4 // base header + mask
                payloadLen <= 65535 -> 2 + 2 + 4 // base + 16-bit len + mask
                else -> 2 + 8 + 4 // base + 64-bit len + mask
            }

            val frame = ByteArray(headerLen + payloadLen)
            var offset = 0

            // First byte
            frame[offset++] = b0.toByte()

            // Second byte + extended length
            when {
                payloadLen <= 125 -> {
                    frame[offset++] = (maskBit or payloadLen).toByte()
                }

                payloadLen <= 65535 -> {
                    frame[offset++] = (maskBit or 126).toByte()
                    frame[offset++] = (payloadLen ushr 8).toByte()
                    frame[offset++] = payloadLen.toByte()
                }

                else -> {
                    frame[offset++] = (maskBit or 127).toByte()

                    for (shift in 56 downTo 0 step 8) {
                        frame[offset++] = (payloadLen.toLong() ushr shift).toByte()
                    }
                }
            }

            // Mask key
            val maskKey = ByteArray(4)
            random.nextBytes(maskKey)

            maskKey.copyInto(frame, offset)
            offset += 4

            // Masked payload
            for (i in 0 until payloadLen) {
                frame[offset + i] = (payload[i].toInt() xor maskKey[i and 0x03].toInt()).toByte()
            }

            return frame
        }

    }

}
