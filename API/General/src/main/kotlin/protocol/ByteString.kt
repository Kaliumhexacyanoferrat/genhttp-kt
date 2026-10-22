package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * An ASCII string read from the request buffer without specific meaning, e.g. a header or query value.
 */
public class ByteString : MemoryView {

    /**
     * Creates a byte string on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates a byte string on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates a byte string from the given (ASCII) string.
     */
    public constructor(value: String) : super(value)

}
