package org.genhttp.modules.io.streaming

import java.io.InputStream

/**
 * A stream that releases the given dependency (such as the connection a stream has been created from) as soon
 * as the stream is closed.
 *
 * @param content The stream to be wrapped
 * @param dependency The resource to be closed together with the stream
 */
public class StreamWithDependency(private val content: InputStream, private val dependency: AutoCloseable) : InputStream() {

    override fun read(): Int = content.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = content.read(b, off, len)

    override fun skip(n: Long): Long = content.skip(n)

    override fun available(): Int = content.available()

    override fun mark(readlimit: Int) {
        content.mark(readlimit)
    }

    override fun reset() {
        content.reset()
    }

    override fun markSupported(): Boolean = content.markSupported()

    override fun close() {
        try {
            content.close()
        } finally {
            dependency.close()
        }
    }

}
