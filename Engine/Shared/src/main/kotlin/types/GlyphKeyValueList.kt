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
