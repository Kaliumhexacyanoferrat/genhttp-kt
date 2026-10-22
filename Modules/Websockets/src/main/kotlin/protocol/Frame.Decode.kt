@file:JvmName("FrameDecoding")

package org.genhttp.modules.websockets.protocol

import org.genhttp.modules.websockets.SocketConnection

import java.nio.ByteBuffer

private const val IncompleteFrame = "Incomplete frame"
private const val InvalidOpCode = "Invalid OpCode"
private const val InvalidControlFrame = "Invalid Control Frame"
private const val InvalidControlFrameLength = "Invalid Control Frame Length"
private const val PayloadTooLarge = "Payload is too large"

private const val MaxFrameHeaderSize = 14

/**
 * The result of decoding a frame.
 *
 * @param frame The decoded frame (or an error frame)
 * @param consumed The number of bytes that have been consumed, relative to the position of the decoded buffer
 * @param examined The number of bytes that have been examined, relative to the position of the decoded buffer
 */
internal class FrameDecodeResult(val frame: ParsedWebsocketFrame, val consumed: Int, val examined: Int)

/*
   Websockets RFC 6455 Frame Decode definition

   The decode algorithm reads a raw WebSocket frame from a buffer and converts it into a structured frame by interpreting
   the bits according to RFC 6455. It begins by ensuring at least the minimum 2-byte header is present, then extracts the FIN bit
   and opcode, which determine whether the frame is a complete message, a fragment, or a control frame such as Ping, Pong, or Close.
   It then checks the MASK bit and the 7-bit base payload length. If the length field indicates an extended payload (126 or 127),
   the algorithm reads the appropriate 16-bit or 64-bit length field and adjusts the header size accordingly. Control-frame rules
   are enforced: control frames must not be fragmented and must not use extended lengths. After determining the final payload length
   and verifying that the buffer contains enough bytes for the full header, mask key, and payload, the decoder extracts the optional
   4-byte mask key and applies the unmasking XOR operation in place if masking is enabled. For normal data frames (Text, Binary,
   Continue), the raw unmasked payload bytes are returned as-is, while Close frames are additionally parsed to extract the close code
   and UTF-8 reason. Finally, the method returns a frame containing the payload, frame type, and FIN flag, leaving higher-level logic
   to handle message reassembly and continuation semantics.
 */

/**
 * Decodes the frame at the start of the given buffer.
 *
 * The data between the position and the limit of the buffer is examined, the buffer must be writable as the
 * payload of masked frames is unmasked in place. The payload of the resulting frame is a view on the buffer.
 *
 * @param connection The connection the frame has been read from
 * @param data The buffer to decode the frame from (neither position nor limit are modified)
 * @param rxMaxBufferSize The maximum size of a frame (including the header)
 */
internal fun Frame.decode(connection: SocketConnection, data: ByteBuffer, rxMaxBufferSize: Int): FrameDecodeResult {
    val start = data.position()
    val end = data.limit()

    var position = start

    fun incomplete() = FrameDecodeResult(
        ParsedWebsocketFrame(connection, FrameError(IncompleteFrame, FrameErrorType.Incomplete)),
        0,
        end - start
    )

    fun failed(message: String, type: FrameErrorType): FrameDecodeResult {
        val offset = position - start

        return FrameDecodeResult(ParsedWebsocketFrame(connection, FrameError(message, type)), offset, offset)
    }

    if (end - position < 2) {
        return incomplete()
    }

    val b0 = data.get(position++).toInt() and 0xFF
    val b1 = data.get(position++).toInt() and 0xFF

    val fin = (b0 and 0b1000_0000) != 0
    val opcode = b0 and 0x0F

    val frameType = when (opcode) {
        0x00 -> FrameType.Continue
        0x01 -> FrameType.Text
        0x02 -> FrameType.Binary
        0x08 -> FrameType.Close
        0x09 -> FrameType.Ping
        0x0A -> FrameType.Pong
        else -> FrameType.None
    }

    if (frameType == FrameType.None) {
        return failed(InvalidOpCode, FrameErrorType.InvalidOpCode)
    }

    val isControlFrame = frameType == FrameType.Close || frameType == FrameType.Ping || frameType == FrameType.Pong
    val isMasked = (b1 and 0x80) != 0
    val payloadLen7 = b1 and 0x7F

    if (isControlFrame && !fin) {
        return failed(InvalidControlFrame, FrameErrorType.InvalidControlFrame)
    }

    if (isControlFrame && payloadLen7 >= 126) {
        return failed(InvalidControlFrameLength, FrameErrorType.InvalidControlFrameLength)
    }

    var payloadLen64 = payloadLen7.toLong()

    if (payloadLen7 == 126) {
        if (end - position < 2) {
            return incomplete()
        }

        payloadLen64 = ((data.get(position).toInt() and 0xFF) shl 8 or (data.get(position + 1).toInt() and 0xFF)).toLong()

        position += 2
    } else if (payloadLen7 == 127) {
        if (end - position < 8) {
            return incomplete()
        }

        var length = 0L

        for (i in 0 until 8) {
            length = (length shl 8) or (data.get(position + i).toLong() and 0xFF)
        }

        payloadLen64 = length

        position += 8
    }

    val maxAllowedPayload = rxMaxBufferSize - MaxFrameHeaderSize

    if (payloadLen64 < 0 || payloadLen64 > maxAllowedPayload || payloadLen64 > Int.MAX_VALUE) {
        return failed(PayloadTooLarge, FrameErrorType.PayloadTooLarge)
    }

    val payloadLength = payloadLen64.toInt()

    val maskKey = ByteArray(4)

    if (isMasked) {
        if (end - position < 4) {
            return incomplete()
        }

        for (i in 0 until 4) {
            maskKey[i] = data.get(position + i)
        }

        position += 4
    }

    if (end - position < payloadLength) {
        return incomplete()
    }

    val payloadStart = position
    val payloadEnd = payloadStart + payloadLength

    // unmask in place
    if (isMasked && payloadLength != 0) {
        for (i in 0 until payloadLength) {
            data.put(payloadStart + i, (data.get(payloadStart + i).toInt() xor maskKey[i and 3].toInt()).toByte())
        }
    }

    position = payloadEnd

    val consumed = position - start

    // close frame
    if (frameType == FrameType.Close) {
        if (payloadLength >= 2) {
            val closeCode = ((data.get(payloadStart).toInt() and 0xFF) shl 8) or (data.get(payloadStart + 1).toInt() and 0xFF)

            var reason: String? = null

            if (payloadLength > 2) {
                val reasonBytes = ByteArray(payloadLength - 2)

                for (i in reasonBytes.indices) {
                    reasonBytes[i] = data.get(payloadStart + 2 + i)
                }

                reason = String(reasonBytes, Charsets.UTF_8)
            }

            val message = "Close frame received. Code: $closeCode, Reason: ${reason ?: "None"}"

            val frame = ParsedWebsocketFrame(connection, ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)).asReadOnlyBuffer(), frameType, fin)

            return FrameDecodeResult(frame, consumed, consumed)
        }

        // empty close payload
        return FrameDecodeResult(ParsedWebsocketFrame(connection, ByteBuffer.allocate(0).asReadOnlyBuffer(), frameType, fin), consumed, consumed)
    }

    // normal frames: return the raw payload slice (zero-copy)
    val payload = data.duplicate().limit(payloadEnd).position(payloadStart).slice().asReadOnlyBuffer()

    return FrameDecodeResult(ParsedWebsocketFrame(connection, payload, frameType, fin), consumed, consumed)
}
