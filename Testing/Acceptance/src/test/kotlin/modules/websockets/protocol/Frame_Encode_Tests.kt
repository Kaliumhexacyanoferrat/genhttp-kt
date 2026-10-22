package org.genhttp.testing.acceptance.modules.websockets.protocol

import org.genhttp.modules.websockets.protocol.Frame

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Frame_Encode_Tests {

    /*
       Encode_TextFrame_SmallPayload_CorrectHeaderAndPayload
       Verifies that small text frames (opcode=0x01, FIN=1) produce:
           Correct first byte (0x81)
           Correct 1-byte payload length
           Correct, unchanged payload bytes
           Runs for 3 different small text payloads.

       Encode_BinaryFrame_SmallPayload_SetsBinaryOpcode
       Ensures a small binary frame (opcode=0x02) sets the correct first byte (0x82) and correctly embeds the payload.

       Encode_TextFrame_NonFinal_ClearsFinBit
       Confirms that when fin=false, the FIN bit is properly cleared (0x01) while encoding text frames.
       Runs for 3 different payloads.

       Encode_Extended126Payload_Writes16BitLength
       Validates that payloads exactly 126 bytes use the correct extended 16-bit length field:
           frame[1] == 126
           Correct big-endian 16-bit length
           Payload follows starting at offset 4.

       Encode_Extended127Payload_Writes64BitLength
       Confirms that very large payloads (>65535 bytes) use the 64-bit extended length field:
           frame[1] == 127
           Correct big-endian 64-bit payload length
           Payload bytes correctly copied (spot-checked).

       Encode_ControlFrame_WithFinFalse_Throws
       Ensures that control frames (opcode >= 0x08) throw an exception when encoded with fin=false, as required by RFC 6455.

       Encode_InvalidOpcodeBelowControlRange_Throws
       Validates that invalid opcodes (e.g., 0x03) correctly throw an exception indicating the opcode is invalid.

       Encode_ControlFrame_Close_SmallPayload_CorrectHeaderAndPayload
       Ensures that close frames (opcode=0x08) with a small payload:
           Use correct first byte (0x88)
           Encode the correct payload length
           Copy the payload bytes unchanged.
     */

    private fun encodeToArray(payload: ByteBuffer, opcode: Byte = 0x01, fin: Boolean = true): ByteArray {
        val writer = ByteArrayOutputStream()
        Frame.write(Channels.newChannel(writer), payload, opcode, fin)
        return writer.toByteArray()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "chunk", "this is a longer text payload"])
    fun encode_TextFrame_SmallPayload_CorrectHeaderAndPayload(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)

        val frame = encodeToArray(ByteBuffer.wrap(payload))

        // FIN=1, opcode=0x01
        assertEquals(0x81, frame[0].toInt() and 0xFF)
        // No MASK bit, length = 2
        assertEquals(payload.size.toByte(), frame[1])

        val actualPayload = ByteArray(payload.size)
        frame.copyInto(actualPayload, 0, 2, 2 + payload.size)

        assertContentEquals(payload, actualPayload)
    }

    @Test
    fun encode_BinaryFrame_SmallPayload_SetsBinaryOpcode() {
        val payload = byteArrayOf(
            0x01, 0x02, 0x03
        )

        val frame = encodeToArray(ByteBuffer.wrap(payload), opcode = 0x02)

        // FIN=1, opcode=0x02
        assertEquals(0x82, frame[0].toInt() and 0xFF)
        // Length = 3
        assertEquals(payload.size.toByte(), frame[1])

        val actualPayload = ByteArray(payload.size)
        frame.copyInto(actualPayload, 0, 2, 2 + payload.size)

        assertContentEquals(payload, actualPayload)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "chunk", "this is a longer text payload"])
    fun encode_TextFrame_NonFinal_ClearsFinBit(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)

        val frame = encodeToArray(ByteBuffer.wrap(payload), opcode = 0x01, fin = false)

        // FIN=0, opcode=0x01 => 0x01
        assertEquals(0x01, frame[0].toInt() and 0x8F) // mask off RSV bits just in case
        assertEquals(payload.size.toByte(), frame[1])
    }

    @Test
    fun encode_Extended126Payload_Writes16BitLength() {
        // Payload length just over small threshold (e.g., 126)
        val payload = ByteArray(126)
        for (i in payload.indices) {
            payload[i] = 'a'.code.toByte()
        }

        val frame = encodeToArray(ByteBuffer.wrap(payload))

        // FIN=1, opcode=0x01
        assertEquals(0x81, frame[0].toInt() and 0xFF)
        // PayloadLen7 = 126
        assertEquals(126, frame[1].toInt())

        val len16 = ByteBuffer.wrap(frame, 2, 2).short.toInt() and 0xFFFF
        assertEquals(payload.size, len16)

        // Payload starts at index 4
        for (i in payload.indices) {
            assertEquals('a'.code.toByte(), frame[4 + i])
        }
    }

    @Test
    fun encode_Extended127Payload_Writes64BitLength() {
        // Payload length > ushort.MaxValue to force 64-bit length field
        val payloadLength = 65535 + 1 // 65536
        val payload = ByteArray(payloadLength)
        for (i in payload.indices) {
            payload[i] = (i and 0xFF).toByte()
        }

        val frame = encodeToArray(ByteBuffer.wrap(payload))

        // FIN=1, opcode=0x01
        assertEquals(0x81, frame[0].toInt() and 0xFF)
        // PayloadLen7 = 127
        assertEquals(127, frame[1].toInt())

        val len64 = ByteBuffer.wrap(frame, 2, 8).long
        assertEquals(payloadLength.toLong(), len64)

        // Spot-check a few payload bytes
        assertEquals(payload[0], frame[10])
        assertEquals(payload[12345], frame[10 + 12345])
        assertEquals(payload.last(), frame.last())
    }

    @Test
    fun encode_ControlFrame_WithFinFalse_Throws() {
        val writer = ByteArrayOutputStream()

        val ex = assertFailsWith<IllegalArgumentException> {
            Frame.write(Channels.newChannel(writer), ByteBuffer.allocate(0), opcode = 0x08, fin = false)
        }

        // the JVM has no counterpart of ArgumentException.ParamName, the message names the parameter instead
        assertTrue(ex.message!!.contains("FIN"))
        assertContains(ex.message!!, "Control frames")
    }

    @Test
    fun encode_InvalidOpcodeBelowControlRange_Throws() {
        val writer = ByteArrayOutputStream()

        val ex = assertFailsWith<IllegalArgumentException> {
            Frame.write(Channels.newChannel(writer), ByteBuffer.allocate(0), opcode = 0x03, fin = true)
        }

        // the JVM has no counterpart of ArgumentException.ParamName, the message names the parameter instead
        assertTrue(ex.message!!.contains("opcode"))
        assertContains(ex.message!!, "Invalid opcode")
    }

    @Test
    fun encode_ControlFrame_Close_SmallPayload_CorrectHeaderAndPayload() {
        // Close frame with small payload
        val payload = byteArrayOf(
            0x03, 0xE8.toByte()
        ) // code 1000

        val frame = encodeToArray(ByteBuffer.wrap(payload), opcode = 0x08, fin = true)

        // FIN=1, opcode=0x08
        assertEquals(0x88, frame[0].toInt() and 0xFF)
        // Length = 2
        assertEquals(payload.size.toByte(), frame[1])

        val actualPayload = ByteArray(payload.size)
        frame.copyInto(actualPayload, 0, 2, 2 + payload.size)

        assertContentEquals(payload, actualPayload)
    }

}
