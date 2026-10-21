package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * Represents an HTTP protocol version.
 */
public class HttpProtocol : MemoryView {

    /**
     * Creates a protocol version on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates a protocol version on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates a protocol version from the given string (e.g. "HTTP/1.1").
     */
    public constructor(value: String) : super(value)

    /**
     * Well-known protocol versions.
     */
    public companion object {

        /** HTTP/1.0 */
        public val Http10: HttpProtocol = HttpProtocol("HTTP/1.0")

        /** HTTP/1.1 */
        public val Http11: HttpProtocol = HttpProtocol("HTTP/1.1")

        /** HTTP/2 */
        public val Http2: HttpProtocol = HttpProtocol("HTTP/2.0")

        /** HTTP/3 */
        public val Http3: HttpProtocol = HttpProtocol("HTTP/3.0")

    }

}
