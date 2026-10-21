package org.genhttp.testing.acceptance.generators

import org.genhttp.api.MemoryView

import org.junit.jupiter.api.Test

import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The source generator of GenHTTP is replaced by the [MemoryView] base type, so these tests
 * verify that types derived from it receive the members the generator used to emit.
 */
class MemoryViewGeneratorTests {


    // region Tests

    @Test
    fun testStructInNamespaceIsGenerated() {
        val value = MyView("Content-Type")

        assertTrue(value.equals(MyView("CONTENT-TYPE")))
        assertEquals(MyView(ascii("Content-Type")), value)
        assertEquals(MyView("content-type").hashCode(), value.hashCode())
        assertEquals("Content-Type", value.toString())
        assertEquals(12, value.length)
        assertTrue(value.contentEquals(ascii("content-TYPE")))
    }

    @Test
    fun testStructInGlobalNamespaceIsGenerated() {
        val value = MyGlobalView("Content-Type")

        assertTrue(value.equals(MyGlobalView("CONTENT-TYPE")))
        assertEquals(MyGlobalView(ascii("Content-Type")), value)
        assertEquals(MyGlobalView("content-type").hashCode(), value.hashCode())
        assertEquals("Content-Type", value.toString())
        assertTrue(value.contentEquals(ascii("content-TYPE")))
    }

    @Test
    fun testStructWithoutAttributeIsIgnored() {
        val value = WithoutBase("Content-Type")

        // no members are generated, so the default (identity based) equality applies
        assertFalse(value.equals(WithoutBase("Content-Type")))
        assertFalse(value.equals(WithoutBase("CONTENT-TYPE")))
        assertTrue(value.equals(value))
    }

    @Test
    fun testMultipleStructsProduceIndependentSources() {
        val first = First("Content-Type")
        val second = Second("Content-Type")

        assertTrue(first.equals(First("CONTENT-TYPE")))
        assertTrue(second.equals(Second("CONTENT-TYPE")))

        assertEquals("Content-Type", first.toString())
        assertEquals("Content-Type", second.toString())

        // equality only holds between instances of the same type
        assertNotEquals<Any>(first, second)
        assertFalse(first.equals(second))
        assertFalse(second.equals(first))
    }

    // endregion

    // region Supporting infrastructure

    class MyView : MemoryView {

        constructor(value: ByteBuffer) : super(value)

        constructor(value: String) : super(value)

    }

    class MyGlobalView : MemoryView {

        constructor(value: ByteBuffer) : super(value)

        constructor(value: String) : super(value)

    }

    class WithoutBase(val value: String)

    class First : MemoryView {

        constructor(value: ByteBuffer) : super(value)

        constructor(value: String) : super(value)

    }

    class Second : MemoryView {

        constructor(value: ByteBuffer) : super(value)

        constructor(value: String) : super(value)

    }

    private fun ascii(value: String): ByteBuffer = ByteBuffer.wrap(value.toByteArray(Charsets.US_ASCII))

    // endregion

}
