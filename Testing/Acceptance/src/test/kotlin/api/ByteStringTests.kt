package org.genhttp.testing.acceptance.api

import org.genhttp.api.protocol.ByteString

import org.junit.jupiter.api.Test

import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByteStringTests {

    @Test
    fun stringAndMemoryConstructors_CreateEqualValues() {
        val fromString = ByteString("Content-Type")
        val fromMemory = ByteString(ByteBuffer.wrap("Content-Type".toByteArray(Charsets.US_ASCII)))

        assertEquals(fromString, fromMemory)
        assertEquals("Content-Type", fromString.toString())
    }

    @Test
    fun equality_IsCaseInsensitive() {
        val lower = ByteString("Content-Type")
        val upper = ByteString("CONTENT-TYPE")
        val mixed = ByteString("CoNtEnT-TyPe")

        assertTrue(lower == upper)
        assertTrue(lower.equals(mixed))
        assertFalse(lower != upper)
    }

    @Test
    fun equality_DifferentValuesAreNotEqual() {
        val first = ByteString("Content-Type")
        val second = ByteString("Content-Length")

        assertFalse(first == second)
        assertTrue(first != second)
        assertFalse(first.equals(second))
    }

    @Test
    fun equality_WithReadOnlyMemory_IsCaseInsensitive() {
        val value = ByteString("Content-Type")
        val memory = ByteBuffer.wrap("CONTENT-TYPE".toByteArray(Charsets.US_ASCII))

        assertTrue(value.contentEquals(memory))
        assertTrue(value.contentEquals(memory))
        assertFalse(!value.contentEquals(memory))
        assertFalse(!value.contentEquals(memory))
        assertTrue(value.contentEquals(memory))
    }

    @Test
    fun equality_WithReadOnlyMemory_DifferentValueIsNotEqual() {
        val value = ByteString("Content-Type")
        val memory = ByteBuffer.wrap("Content-Length".toByteArray(Charsets.US_ASCII))

        assertFalse(value.contentEquals(memory))
        assertFalse(value.contentEquals(memory))
        assertTrue(!value.contentEquals(memory))
        assertTrue(!value.contentEquals(memory))
    }

    @Test
    fun equalValues_HaveEqualHashCodes() {
        val lower = ByteString("Content-Type")
        val upper = ByteString("CONTENT-TYPE")

        assertEquals(lower.hashCode(), upper.hashCode())
    }

    @Test
    fun dictionary_IsCaseInsensitive() {
        val dictionary = mutableMapOf(ByteString("Content-Type") to "text/plain")

        assertEquals("text/plain", dictionary[ByteString("content-type")])

        assertTrue(dictionary.containsKey(ByteString("CONTENT-TYPE")))
    }

    @Test
    fun dictionary_DifferentCasingDoesNotCreateDuplicateKey() {
        val dictionary = mutableMapOf(ByteString("Subdir") to 1)

        dictionary[ByteString("SUBDIR")] = 2

        assertEquals(1, dictionary.size)
        assertEquals(2, dictionary[ByteString("subdir")])
    }

    @Test
    fun hashSet_IsCaseInsensitive() {
        val set = mutableSetOf(ByteString("Subdir"))

        assertTrue(set.contains(ByteString("SUBDIR")))
        assertFalse(set.add(ByteString("subdir")))
        assertEquals(1, set.size)
    }

    @Test
    fun objectAndGenericEquality_AreCaseInsensitive() {
        val value = ByteString("Subdir")

        val other: Any = ByteString("SUBDIR")

        assertTrue(value.equals(other))
        assertTrue(value.equals(ByteString("SUBDIR")))
    }

    @Test
    fun readOnlyMemorySlice_IsComparedUsingVisibleRange() {
        val bytes = "xxxCONTENT-TYPExxx".toByteArray(Charsets.US_ASCII)

        val slice = ByteBuffer.wrap(bytes, 3, "CONTENT-TYPE".length).slice()

        val value = ByteString("content-type")

        assertTrue(value.contentEquals(slice))
        assertTrue(value.contentEquals(slice))
    }

    @Test
    fun emptyValues_AreEqual() {
        val value = ByteString("")

        assertTrue(value == ByteString(ByteBuffer.allocate(0)))
        assertTrue(value.contentEquals(ByteBuffer.allocate(0)))
        assertTrue(value.contentEquals(ByteBuffer.allocate(0)))
    }

    @Test
    fun equalityAndInequality_AreConsistent() {
        val equal = ByteString("Subdir")
        val same = ByteString("SUBDIR")
        val different = ByteString("Other")

        assertEquals(equal == same, !(equal != same))
        assertEquals(equal == different, !(equal != different))
    }

}
