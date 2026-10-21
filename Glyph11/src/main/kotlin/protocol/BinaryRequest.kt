package org.genhttp.glyph11.protocol

import org.genhttp.glyph11.EmptyBuffer
import java.nio.ByteBuffer

/**
 * Holds the parsed components of an HTTP/1.1 request header.
 *
 * All byte-level fields are read-only [ByteBuffer] slices of the original input (their content is
 * exactly the field bytes).
 *
 * Reuse instances across requests by calling [clear] between parses. Call [close] when the
 * instance is no longer needed to release the storage used by [headers] and [queryParameters].
 */
public class BinaryRequest : AutoCloseable {

    private val headerList = KeyValueList()

    private val queryList = KeyValueList()

    /** HTTP version string, e.g. "HTTP/1.1". Set by UltraHardenedParser only. */
    public var version: ByteBuffer = EmptyBuffer
        internal set

    /** HTTP method, e.g. "GET", "POST". */
    public var method: ByteBuffer = EmptyBuffer
        internal set

    /** Request path without the query string, e.g. "/api/users". */
    public var path: ByteBuffer = EmptyBuffer
        internal set

    /** Parsed query parameters as key-value pairs. */
    public val queryParameters: KeyValueList
        get() = queryList

    /** Parsed HTTP headers as key-value pairs. */
    public val headers: KeyValueList
        get() = headerList

    /** Request body bytes. Not populated by the header parser. */
    public var body: ByteBuffer = EmptyBuffer
        internal set

    /**
     * Resets the request for reuse. Clears headers and query parameters
     * but keeps the underlying storage allocated.
     */
    public fun clear() {
        version = EmptyBuffer
        method = EmptyBuffer
        path = EmptyBuffer
        body = EmptyBuffer

        headerList.clear()
        queryList.clear()
    }

    /**
     * Releases the storage used by [headers] and [queryParameters].
     * The instance should not be used after closing.
     */
    override fun close() {
        headerList.close()
        queryList.close()
    }

}
