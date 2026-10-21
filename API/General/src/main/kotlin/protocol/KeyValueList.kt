package org.genhttp.api.protocol

import java.nio.ByteBuffer

/**
 * An iterable list of key/value pairs read from the request buffer.
 *
 * Does not implement [Iterable] to stay allocation-free. Does not use map semantics
 * as there can be multiple headers with the same name.
 */
public interface KeyValueList {

    /**
     * The number of entries in this list.
     */
    public val count: Int

    /**
     * Fetches the entry with the given index.
     *
     * The returned buffers are read-only views (content from position to limit).
     *
     * @param index The zero based index of the entry to fetch
     */
    public fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer>

}
