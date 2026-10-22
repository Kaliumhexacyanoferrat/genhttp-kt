package org.genhttp.testing.acceptance.modules.websockets.protocol

import org.genhttp.modules.websockets.protocol.Frame

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class Frame_Encode_CloseAsync_Tests {

    /*

       EncodeClose_NoReason_BuildsValidCloseFrame
       Verifies that a close frame with no reason uses the correct header, payload length, and encodes the status code properly.

       EncodeClose_WithReason_BuildsValidCloseFrame
       Ensures that when a reason is included, the payload length is correct and the UTF-8 reason bytes and status code are encoded properly.

       EncodeClose_TooLongReason_ThrowsArgumentException
       Confirms that providing a reason longer than the allowed 125-byte control-frame limit results in an exception.

       EncodeClose_UsesFinBitAndCloseOpcode
       Checks that the FIN flag is set, the opcode is 0x08 (Close), and the overall frame length is correct.

     */

    private fun encodeCloseToArray(reason: String?, statusCode: Int): ByteArray {
        val writer = ByteArrayOutputStream()
        Frame.writeClose(Channels.newChannel(writer), reason, statusCode)
        return writer.toByteArray()
    }

    @Test
    fun encodeClose_NoReason_BuildsValidCloseFrame() {
        val statusCode = 1000 // Normal closure
        val payloadLength = 2 // Only status code
        val frameLength = 2 + payloadLength

        val frame = encodeCloseToArray(reason = null, statusCode)

        // Assert: header
        assertEquals(0x88, frame[0].toInt() and 0xFF, "FIN + opcode for Close must be 0x88 (FIN=1, opcode=0x8).")
        assertEquals(payloadLength.toByte(), frame[1], "Payload length must equal 2 (status code only).")
        assertEquals(frameLength, frame.size, "Frame length must be 4 bytes.")

        // Assert: payload (status code, big-endian)
        val code = ByteBuffer.wrap(frame, 2, 2).short.toInt() and 0xFFFF
        assertEquals(statusCode, code, "Status code must be written in big-endian.")
    }

    @Test
    fun encodeClose_WithReason_BuildsValidCloseFrame() {
        val statusCode = 1001
        val reason = "Going away"
        val reasonBytes = reason.toByteArray(Charsets.UTF_8)
        val payloadLength = 2 + reasonBytes.size
        val frameLength = 2 + payloadLength

        val frame = encodeCloseToArray(reason, statusCode)

        // Assert: header
        assertEquals(0x88, frame[0].toInt() and 0xFF, "FIN + opcode for Close must be 0x88 (FIN=1, opcode=0x8).")
        assertEquals(payloadLength.toByte(), frame[1], "Payload length must match status code + reason.")
        assertEquals(frameLength, frame.size, "Frame length must be header + payload.")

        // Assert: status code
        val code = ByteBuffer.wrap(frame, 2, 2).short.toInt() and 0xFFFF
        assertEquals(statusCode, code, "Status code must be written in big-endian.")

        // Assert: reason string
        val actualReasonBytes = frame.copyOfRange(4, 4 + reasonBytes.size)
        assertContentEquals(reasonBytes, actualReasonBytes, "Reason bytes must match UTF-8 encoding of the reason string.")
    }

    @Test
    fun encodeClose_TooLongReason_ThrowsArgumentException() {
        val tooLongReason = "a".repeat(200)
        val writer = ByteArrayOutputStream()

        assertFailsWith<IllegalArgumentException> {
            Frame.writeClose(Channels.newChannel(writer), tooLongReason, 1000)
        }
    }

    @Test
    fun encodeClose_UsesFinBitAndCloseOpcode() {
        val statusCode = 1000
        val reason = "Test"

        val reasonBytes = reason.toByteArray(Charsets.UTF_8)
        val payloadLength = 2 + reasonBytes.size
        val frameLength = 2 + payloadLength

        val frame = encodeCloseToArray(reason, statusCode)

        val finAndOpcode = frame[0].toInt() and 0xFF

        // FIN must be set
        assertNotEquals(0, finAndOpcode and 0x80, "FIN bit must be set for Close frames.")

        // Opcode must be 0x8
        val opcode = finAndOpcode and 0x0F
        assertEquals(0x08, opcode, "Opcode must be 0x8 for Close frames.")

        // Sanity check on total length
        assertEquals(frameLength, frame.size)
    }

}
