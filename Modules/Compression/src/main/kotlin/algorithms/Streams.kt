package org.genhttp.modules.compression.algorithms

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

/**
 * Passes everything to the wrapped stream, but never closes it (`leaveOpen: true` in .NET).
 */
internal class NonClosingOutputStream(target: OutputStream) : FilterOutputStream(target) {

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
    }

    override fun close() {
        // the target is owned by the caller
    }

}

/**
 * Passes everything to the wrapped stream, but never closes it (`leaveOpen: true` in .NET).
 */
internal class NonClosingInputStream(source: InputStream) : FilterInputStream(source) {

    override fun close() {
        // the source is owned by the caller
    }

}

/**
 * Creates the actual stream on the first read, which allows to read from empty sources without
 * failing (the .NET decompression streams do not read any header on construction).
 */
internal class LazyInputStream(private val factory: () -> InputStream) : InputStream() {

    private var delegate: InputStream? = null

    private fun stream(): InputStream = delegate ?: factory().also { delegate = it }

    override fun read(): Int = stream().read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = stream().read(b, off, len)

    override fun available(): Int = stream().available()

    override fun skip(n: Long): Long = stream().skip(n)

    override fun close() {
        delegate?.close()
    }

}

/**
 * Wraps the given source into a stream that decompresses the data on read. The source is not closed
 * when the returned stream is closed. An empty source results in an empty stream.
 *
 * @param source The compressed data
 * @param decompressor Creates the stream that decompresses the data read from the source
 */
internal fun decompressing(source: InputStream, decompressor: (InputStream) -> InputStream): InputStream = LazyInputStream {
    val input = PushbackInputStream(NonClosingInputStream(source), 1)

    val first = input.read()

    if (first < 0) {
        InputStream.nullInputStream()
    } else {
        input.unread(first)
        decompressor(input)
    }
}
