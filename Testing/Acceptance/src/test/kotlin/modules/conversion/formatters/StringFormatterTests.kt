package org.genhttp.testing.acceptance.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.conversion.formatters.StringFormatter

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringFormatterTests {

    private val formatter = StringFormatter()

    private fun bytes(s: String): ByteString = ByteString(s.toByteArray(Charsets.UTF_8))

    @Test
    fun handlesOnlyString() {
        assertTrue(formatter.canHandle(String::class))
        assertFalse(formatter.canHandle(Int::class))
        assertFalse(formatter.canHandle(Boolean::class))
    }

    @Test
    fun readsAsciiString() {
        assertEquals("hello", formatter.read(bytes("hello"), String::class))
    }

    @Test
    fun readsEmptyString() {
        assertEquals("", formatter.read(bytes(""), String::class))
    }

    @Test
    fun readsUtf8String() {
        assertEquals("héllo", formatter.read(bytes("héllo"), String::class))
    }

    @Test
    fun writesString() {
        assertEquals("hello", formatter.write("hello", String::class))
    }

}
