package org.genhttp.modules.websockets.protocol

import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Encodes and decodes websocket frames as specified by RFC 6455.
 *
 * A websocket frame is built by constructing a small header followed by the raw payload bytes. The first header
 * byte contains the FIN bit and the opcode, indicating whether the frame is the final fragment of a message and
 * whether it represents text, binary, ping, pong, or close. The second header byte contains the MASK bit (set only
 * for frames sent by a client) and a 7-bit payload length, which may either store the actual length (0-125) or a
 * special marker (126 or 127) indicating that an extended 16-bit or 64-bit length field follows. After the length
 * fields, the optional 4-byte masking key appears for client-to-server frames; the payload must then be XOR-masked
 * with this key. The server typically sends unmasked frames, producing the sequence: header, extended length
 * fields (if any), payload.
 */
public object Frame {

    private const val MaxSmallPayloadLength = 125

    private const val MaxMediumPayloadLength = 65535

    /**
     * Writes a websocket frame directly into the given writer, writing the header first and
     * the payload once afterward.
     *
     * @param writer The writer to write the frame to
     * @param payload The payload of the frame (the data between position and limit, the buffer is not modified)
     * @param opcode The operation code of the frame
     * @param fin Whether this is the final frame of a message
     * @throws IllegalArgumentException Thrown, if the opcode is invalid or if a control frame is fragmented
     */
    public fun write(writer: WritableByteChannel, payload: ByteBuffer, opcode: Byte = 0x01, fin: Boolean = true) {
        val code = opcode.toInt() and 0xFF

        val payloadLength = payload.remaining()

        if (code != 0x01 && code != 0x02 && code < 0x08) {
            throw IllegalArgumentException("Invalid opcode. Must be 0x01 (text), 0x02 (binary), or a control frame opcode (>= 0x08).")
        }

        if (code >= 0x08 && !fin) {
            throw IllegalArgumentException("Control frames (opcode >= 0x08) must not be fragmented (FIN must be true).")
        }

        val headerLength = when {
            payloadLength <= MaxSmallPayloadLength -> 2
            payloadLength <= MaxMediumPayloadLength -> 4
            else -> 10
        }

        val header = ByteArray(headerLength)

        val finBit = if (fin) 0x80 else 0x00
        header[0] = (finBit or code).toByte()

        when {
            payloadLength <= MaxSmallPayloadLength -> header[1] = payloadLength.toByte()

            payloadLength <= MaxMediumPayloadLength -> {
                header[1] = 126
                header[2] = (payloadLength ushr 8).toByte()
                header[3] = payloadLength.toByte()
            }

            else -> {
                header[1] = 127

                // the upper four bytes of the 64 bit length are always zero
                header[6] = (payloadLength ushr 24).toByte()
                header[7] = (payloadLength ushr 16).toByte()
                header[8] = (payloadLength ushr 8).toByte()
                header[9] = payloadLength.toByte()
            }
        }

        writeFully(writer, ByteBuffer.wrap(header))

        if (payloadLength > 0) {
            writeFully(writer, payload.duplicate())
        }
    }

    /**
     * Writes a ping frame (without payload) into the given writer.
     *
     * @param writer The writer to write the frame to
     */
    public fun writePing(writer: WritableByteChannel): Unit = write(writer, ByteBuffer.allocate(0), opcode = 0x09, fin = true)

    /**
     * Writes a pong frame into the given writer.
     *
     * @param writer The writer to write the frame to
     * @param payload The payload of the frame (the data between position and limit, the buffer is not modified)
     */
    public fun writePong(writer: WritableByteChannel, payload: ByteBuffer): Unit = write(writer, payload, opcode = 0x0A, fin = true)

    /**
     * Writes a close frame into the given writer.
     *
     * @param writer The writer to write the frame to
     * @param reason The reason for closing the connection (must fit into 125 bytes including the status code)
     * @param statusCode The status code to be sent to the client (an unsigned 16 bit value)
     * @throws IllegalArgumentException Thrown, if the reason is too long or the status code is out of range
     */
    public fun writeClose(writer: WritableByteChannel, reason: String?, statusCode: Int) {
        require(statusCode in 0..0xFFFF) { "Status code must be an unsigned 16 bit value" }

        if (reason.isNullOrEmpty()) {
            val status = byteArrayOf((statusCode ushr 8).toByte(), statusCode.toByte())

            write(writer, ByteBuffer.wrap(status), opcode = 0x08, fin = true)
            return
        }

        val reasonBytes = reason.toByteArray(Charsets.UTF_8)
        val payloadLength = 2 + reasonBytes.size

        if (payloadLength > 125) {
            throw IllegalArgumentException("Close reason too long (must fit in 125 bytes including the 2-byte status code).")
        }

        val payload = ByteArray(payloadLength)

        payload[0] = (statusCode ushr 8).toByte()
        payload[1] = statusCode.toByte()

        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.size)

        write(writer, ByteBuffer.wrap(payload), opcode = 0x08, fin = true)
    }

    private fun writeFully(writer: WritableByteChannel, data: ByteBuffer) {
        while (data.hasRemaining()) {
            writer.write(data)
        }
    }

}
