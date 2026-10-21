package org.genhttp.api.content.io

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * Identifies a compression algorithm by name (e.g. gzip).
 */
public class AlgorithmName : MemoryView {

    /**
     * Creates an algorithm name on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates an algorithm name on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates an algorithm name from the given string (e.g. "gzip").
     */
    public constructor(value: String) : super(value)

}
