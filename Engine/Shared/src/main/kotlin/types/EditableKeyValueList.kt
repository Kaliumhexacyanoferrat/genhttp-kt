package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.KeyValueList
import org.genhttp.api.protocol.RequestHeaders
import org.genhttp.api.protocol.RequestQuery

import java.nio.ByteBuffer

import org.genhttp.glyph11.protocol.KeyValueList as GlyphList

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
