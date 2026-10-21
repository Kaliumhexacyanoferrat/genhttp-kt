package org.genhttp.api.content.io

/**
 * Specifies whether a compression operation emphasizes speed or compression size
 * (`System.IO.Compression.CompressionLevel` in .NET).
 */
public enum class CompressionLevel {

    /** The compression operation should balance compression speed and output size. */
    Optimal,

    /** The compression operation should complete as quickly as possible, even if the resulting data is not optimally compressed. */
    Fastest,

    /** No compression should be performed. */
    NoCompression,

    /** The compression operation should create output as small as possible, even if the operation takes a longer time to complete. */
    SmallestSize

}
