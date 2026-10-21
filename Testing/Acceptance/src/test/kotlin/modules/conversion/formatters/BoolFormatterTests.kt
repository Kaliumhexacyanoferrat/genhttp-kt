package org.genhttp.testing.acceptance.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.conversion.formatters.BoolFormatter
import org.genhttp.modules.conversion.formatters.read

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoolFormatterTests {

    private val formatter = BoolFormatter()

    private fun bytes(s: String): ByteString = ByteString(s)

    @Test
    fun handlesOnlyBool() {
        assertTrue(formatter.canHandle(Boolean::class))
        assertFalse(formatter.canHandle(Int::class))
        assertFalse(formatter.canHandle(String::class))
    }

    @Test
    fun readsTruthyValues() {
        assertEquals(true, formatter.read(bytes("1"), Boolean::class))
        assertEquals(true, formatter.read(bytes("on"), Boolean::class))
        assertEquals(true, formatter.read(bytes("true"), Boolean::class))
    }

    @Test
    fun readsFalsyValues() {
        assertEquals(false, formatter.read(bytes("0"), Boolean::class))
        assertEquals(false, formatter.read(bytes("off"), Boolean::class))
        assertEquals(false, formatter.read(bytes("false"), Boolean::class))
    }

    @Test
    fun readsCaseInsensitive() {
        assertEquals(true, formatter.read(bytes("True"), Boolean::class))
        assertEquals(true, formatter.read(bytes("ON"), Boolean::class))
        assertEquals(false, formatter.read(bytes("False"), Boolean::class))
        assertEquals(false, formatter.read(bytes("OFF"), Boolean::class))
    }

    @Test
    fun throwsForUnrecognizedInput() {
        assertFailsWith<IllegalArgumentException> { formatter.read(bytes("yes"), Boolean::class) }
        assertFailsWith<IllegalArgumentException> { formatter.read(bytes("maybe"), Boolean::class) }
    }

    @Test
    fun readGenericTruthyValues() {
        assertEquals(true, formatter.read<Boolean>(bytes("1")))
        assertEquals(true, formatter.read<Boolean>(bytes("on")))
        assertEquals(true, formatter.read<Boolean>(bytes("true")))
    }

    @Test
    fun readGenericFalsyValues() {
        assertEquals(false, formatter.read<Boolean>(bytes("0")))
        assertEquals(false, formatter.read<Boolean>(bytes("off")))
        assertEquals(false, formatter.read<Boolean>(bytes("false")))
    }

    @Test
    fun readGenericThrowsForUnrecognizedInput() {
        assertFailsWith<IllegalArgumentException> { formatter.read<Boolean>(bytes("yes")) }
    }

    @Test
    fun writesTrueAsOne() = assertEquals("1", formatter.write(true, Boolean::class))

    @Test
    fun writesFalseAsZero() = assertEquals("0", formatter.write(false, Boolean::class))

    @Test
    fun getContentReturnsTrueContent() {
        assertNotNull(formatter.getContent(true))
    }

    @Test
    fun getContentReturnsFalseContent() {
        assertNotNull(formatter.getContent(false))
    }

    @Test
    fun getContentThrowsForNonBool() {
        assertFailsWith<UnsupportedOperationException> { formatter.getContent(42) }
    }

}
