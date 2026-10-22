package org.genhttp.testing.acceptance.modules.websockets.protocol

import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.modules.websockets.protocol.Frame
import org.genhttp.modules.websockets.protocol.FrameErrorType
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.protocol.decode
import org.genhttp.modules.websockets.readPayload
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class Frame_Decode_Tests {

    /*
       Decode_IncompleteHeader_ReturnsErrorIncomplete
       Ensures that frames with fewer than 2 bytes return an Incomplete error.

       Decode_InvalidOpcode_ReturnsInvalidOpCodeError
       Checks that reserved/unknown opcodes produce an InvalidOpCode error.

       Decode_ControlFrameFragmented_ReturnsInvalidControlFrameError
       Validates that control frames with FIN = 0 (fragmented) return an error.

       Decode_ControlFrameTooLong_ReturnsInvalidControlFrameLengthError
       Ensures control frames with payload lengths >= 126 are rejected.

       Decode_MaskedTextFrame_UnmasksPayloadCorrectly
       Verifies that masked text frames are properly unmasked to their original payload.

       Decode_CloseFrame_WithCodeAndReason_FormatsMessage
       Confirms that Close frames decode the close code and reason, producing the formatted message.

       Decode_CloseFrame_WithoutPayload_AllowsEmpty
       Ensures Close frames with zero-length payloads decode successfully.

       Decode_Extended126_UnmaskedTextFrame_Works
       Tests decoding of unmasked text frames using 16-bit extended payload lengths.

       Decode_Extended127_PayloadTooLarge_ReturnsError
       Ensures payload sizes larger than the configured rxMaxBufferSize return a PayloadTooLarge error.

       Decode_IncompleteExtendedFrame_ReturnsIncompleteError
       Checks that frames indicating extended lengths but missing required bytes return an Incomplete error.
     */

    private val defaultRxMaxBufferSize = 4096

    @Test
    fun decode_IncompleteHeader_ReturnsErrorIncomplete() {
        // Only 1 byte -> < 2
        val frame = byteArrayOf(
            0x81.toByte()
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertError(result.frame, FrameErrorType.Incomplete)

        // For incomplete: do not consume anything, examine everything
        assertEquals(0, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_InvalidOpcode_ReturnsInvalidOpCodeError() {
        // FIN=1, opcode=0x03 (reserved)
        val frame = byteArrayOf(
            0x83.toByte(), // 1000 0011 => FIN + opcode 3
            0x00  // no mask, length 0
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertError(result.frame, FrameErrorType.InvalidOpCode)

        // We read the whole 2-byte header, so consumed/examined == End
        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_ControlFrameFragmented_ReturnsInvalidControlFrameError() {
        // FIN=0, opcode=0x09 (Ping, control frame) -> invalid (control MUST NOT be fragmented)
        val frame = byteArrayOf(
            0x09, // 0000 1001 => no FIN, opcode Ping
            0x00  // no mask, length 0
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertError(result.frame, FrameErrorType.InvalidControlFrame)

        // Again we've consumed the 2-byte header
        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_ControlFrameTooLong_ReturnsInvalidControlFrameLengthError() {
        // Ping with payloadLen7 = 126 (0x7E) -> illegal for control frames
        val frame = byteArrayOf(
            0x89.toByte(), // FIN=1, opcode=Ping
            0x7E  // MASK=0, payloadLen7=126
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertError(result.frame, FrameErrorType.InvalidControlFrameLength)

        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_MaskedTextFrame_UnmasksPayloadCorrectly() = test {
        val payloadString = "Apple pie banana sugar"

        val payload = payloadString.toByteArray(Charsets.UTF_8)
        val maskKey = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val maskedPayload = ByteArray(payload.size)
        for (i in payload.indices) {
            maskedPayload[i] = (payload[i].toInt() xor maskKey[i and 0x03].toInt()).toByte()
        }

        val frame = ByteArray(2 + 4 + maskedPayload.size)
        frame[0] = 0x81.toByte() // FIN=1, Text
        frame[1] = (0x80 or payload.size).toByte() // MASK bit + length
        maskKey.copyInto(frame, 2, 0, 4)
        maskedPayload.copyInto(frame, 6, 0, maskedPayload.size)

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertEquals(FrameType.Text, result.frame.type)
        assertTrue(result.frame.fin)
        assertEquals(payloadString, result.frame.readPayload<String>())

        // Full frame consumed
        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_CloseFrame_WithCodeAndReason_FormatsMessage() = test {
        // Close frame, FIN=1, opcode=0x08, masked
        // Payload: [1000 big endian][UTF8("Bye")]
        val closeCode = 1000
        val reasonBytes = "Bye".toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + reasonBytes.size)

        payload[0] = (closeCode ushr 8).toByte()
        payload[1] = closeCode.toByte()
        reasonBytes.copyInto(payload, 2, 0, reasonBytes.size)

        val maskKey = byteArrayOf(0x10, 0x20, 0x30, 0x40)

        val maskedPayload = ByteArray(payload.size)
        for (i in payload.indices) {
            maskedPayload[i] = (payload[i].toInt() xor maskKey[i and 0x03].toInt()).toByte()
        }

        val frame = ByteArray(2 + 4 + maskedPayload.size)
        frame[0] = 0x88.toByte() // FIN=1, Close
        frame[1] = (0x80 or payload.size).toByte() // MASK bit + length
        maskKey.copyInto(frame, 2, 0, 4)
        maskedPayload.copyInto(frame, 6, 0, maskedPayload.size)

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertEquals(FrameType.Close, result.frame.type)
        assertEquals("Close frame received. Code: 1000, Reason: Bye", result.frame.readPayload<String>())

        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_CloseFrame_WithoutPayload_AllowsEmpty() {
        // Close frame with no payload
        val frame = byteArrayOf(
            0x88.toByte(), // FIN=1, Close
            0x00  // no mask, length 0
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertEquals(FrameType.Close, result.frame.type)
        assertEquals(0, result.frame.data.remaining())

        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_Extended126_UnmaskedTextFrame_Works() {
        // Text frame with payload length = 126 (extended 16-bit)
        val payload = ByteArray(126)
        for (i in payload.indices) {
            payload[i] = 'a'.code.toByte()
        }

        val frame = ByteArray(2 + 2 + payload.size)
        frame[0] = 0x81.toByte() // FIN=1, Text
        frame[1] = 0x7E // MASK=0, payloadLen7=126

        // Extended length (16-bit BE)
        frame[2] = (payload.size ushr 8).toByte()
        frame[3] = payload.size.toByte()
        payload.copyInto(frame, 4, 0, payload.size)

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertEquals(FrameType.Text, result.frame.type)
        assertEquals(126, result.frame.data.remaining())

        val data = result.frame.data

        while (data.hasRemaining()) {
            assertEquals('a'.code.toByte(), data.get())
        }

        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_Extended127_PayloadTooLarge_ReturnsError() {
        // Text frame with extended 64-bit length >> rxMaxBufferSize
        val frame = ByteArray(10)
        frame[0] = 0x81.toByte() // FIN=1, Text
        frame[1] = 0x7F // MASK=0, payloadLen7=127

        // 64-bit payload length = 2^31 (0x0000000080000000) - way larger than rxMaxBufferSize
        val lengthBytes = ByteBuffer.allocate(8).putLong(0x0000000080000000L).array()
        lengthBytes.copyInto(frame, 2, 0, 8)

        val smallRxBufferSize = 1024 // Ensure maxAllowedPayload << 2^31

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), smallRxBufferSize)

        assertError(result.frame, FrameErrorType.PayloadTooLarge)

        assertEquals(frame.size, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    @Test
    fun decode_IncompleteExtendedFrame_ReturnsIncompleteError() {
        // Indicate extended length (126) but not enough bytes for the extended length field.
        val frame = byteArrayOf(
            0x81.toByte(), // FIN=1, Text
            0x7E  // MASK=0, payloadLen7=126 => expects 2 more bytes for length but they're missing
        )

        val result = Frame.decode(MockConnection(), ByteBuffer.wrap(frame), defaultRxMaxBufferSize)

        assertError(result.frame, FrameErrorType.Incomplete)

        // Incomplete => nothing consumed, everything examined
        assertEquals(0, result.consumed)
        assertEquals(frame.size, result.examined)
    }

    private fun assertError(frame: WebsocketFrame, expected: FrameErrorType) {
        assertEquals(FrameType.Error, frame.type)

        val error = frame.isError()

        if (error != null) {
            assertEquals(expected, error.errorType)
        } else {
            fail("Did not return an error frame")
        }
    }

}
