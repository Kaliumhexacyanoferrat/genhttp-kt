package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.KeyValueList
import org.genhttp.api.protocol.RequestHeaders
import org.genhttp.api.protocol.RequestQuery

import java.nio.ByteBuffer

import org.genhttp.glyph11.protocol.KeyValueList as GlyphList

/**
 * Exposes a list parsed by glyph11 (`KeyValueList` in GenHTTP).
 */
public class GlyphKeyValueList(private val source: GlyphList) : RequestHeaders, RequestQuery {

    override val count: Int
        get() = source.count

    override fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer> = source[index]

}

/**
 * A list of key value pairs that can be modified (used for response headers).
 */
public class EditableKeyValueList : RequestHeaders, RequestQuery {

    private val store = ArrayList<Pair<ByteBuffer, ByteBuffer>>()

    override val count: Int
        get() = store.size

    override fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer> = store[index]

    /**
     * Adds the given entry to the list.
     */
    public fun add(key: ByteString, value: ByteString) {
        store.add(key.bytes to value.bytes)
    }

    /**
     * Removes all entries.
     */
    public fun clear() {
        store.clear()
    }

}

/**
 * A copy of a key value list that does no longer depend on the request buffer.
 */
public class RetainedKeyValueList(source: KeyValueList) : RequestHeaders, RequestQuery {

    private val items: List<Pair<ByteBuffer, ByteBuffer>> = List(source.count) { i ->
        val (key, value) = source.getMemoryEntry(i)
        copy(key) to copy(value)
    }

    override val count: Int
        get() = items.size

    override fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer> = items[index]

    private companion object {

        fun copy(buffer: ByteBuffer): ByteBuffer {
            val array = ByteArray(buffer.remaining())
            buffer.get(buffer.position(), array)
            return ByteBuffer.wrap(array).asReadOnlyBuffer()
        }

    }

}
