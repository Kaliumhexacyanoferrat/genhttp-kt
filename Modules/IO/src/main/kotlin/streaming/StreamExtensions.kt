@file:JvmName("StreamExtensions")

package org.genhttp.modules.io.streaming

import org.genhttp.api.infrastructure.BufferSize

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Copies the remaining content of this stream to the given target using a buffer of the specified size.
 *
 * @param target The stream to write the content to
 * @param bufferSize The size of the buffer used to transfer the data
 */
public suspend fun InputStream.copyPooled(target: OutputStream, bufferSize: Int) {
    val buffer = BytePool.rent(bufferSize)

    try {
        while (true) {
            val read = read(buffer)

            if (read < 0) {
                break
            }

            if (read > 0) {
                target.write(buffer, 0, read)
            }
        }
    } finally {
        BytePool.release(buffer)
    }
}

/**
 * Efficiently calculates the checksum of the stream, beginning from the current position. Resets the position
 * to the previous one.
 *
 * Streams that cannot be reset (see [InputStream.markSupported]) cannot be checked as this would consume them.
 *
 * @return The checksum of the stream, or null if the stream does not support resetting
 */
public suspend fun InputStream.calculateChecksum(): Long? {
    if (!markSupported()) {
        return null
    }

    mark(Int.MAX_VALUE)

    try {
        return computeChecksum()
    } finally {
        reset()
    }
}

/**
 * Reads the remaining content of this stream to the given writer.
 *
 * @param target The writer to write the content to
 */
public suspend fun InputStream.write(target: WritableByteChannel) {
    val array = BytePool.rent(BufferSize.Write)

    try {
        val buffer = ByteBuffer.wrap(array)

        while (true) {
            val read = read(array)

            if (read < 0) {
                break
            }

            if (read > 0) {
                buffer.clear()
                buffer.limit(read)

                target.writeFully(buffer)
            }
        }
    } finally {
        BytePool.release(array)
    }
}

/**
 * Calculates the checksum by consuming the remaining content of the stream.
 */
internal fun InputStream.computeChecksum(): Long {
    var hash = 17L

    val buffer = BytePool.rent(4096)

    try {
        while (true) {
            val read = read(buffer)

            if (read < 0) {
                break
            }

            for (i in 0 until read) {
                hash = hash * 23 + (buffer[i].toLong() and 0xFF)
            }
        }
    } finally {
        BytePool.release(buffer)
    }

    return hash
}

/**
 * Calculates the checksum of the given bytes (the same algorithm as used for streams).
 */
internal fun ByteArray.computeChecksum(): Long {
    var hash = 17L

    for (value in this) {
        hash = hash * 23 + (value.toLong() and 0xFF)
    }

    return hash
}

/**
 * Calculates the checksum of the content of the buffer without changing its position.
 */
internal fun ByteBuffer.computeChecksum(): Long {
    var hash = 17L

    val start = position()

    for (i in 0 until remaining()) {
        hash = hash * 23 + (get(start + i).toLong() and 0xFF)
    }

    return hash
}
