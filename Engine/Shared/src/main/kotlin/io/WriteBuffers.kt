package org.genhttp.engine.shared.io

import org.genhttp.api.infrastructure.BufferSize

import java.nio.ByteBuffer

/**
 * Pools the direct buffers used to write responses (GenHTTP rents the memory of its pipes from a pool as well).
 *
 * Connections only hold a buffer while they have data to send, so the buffers are not allocated (and zeroed) for
 * every connection and few buffers serve many connections. The pool is split into stripes to avoid contention.
 */
internal object WriteBuffers {

    private const val BuffersPerStripe = 4

    private val stripes = Array(Integer.highestOneBit(Runtime.getRuntime().availableProcessors()) * 2) { Stripe() }

    private val mask = stripes.size - 1

    /**
     * Returns an empty buffer of the given capacity (only buffers of the default size are pooled).
     */
    fun rent(capacity: Int): ByteBuffer {
        if (capacity == BufferSize.Write) {
            stripe().pop()?.let { return it }
        }

        return ByteBuffer.allocateDirect(capacity)
    }

    /**
     * Hands the given buffer back to the pool, the caller must not use it anymore.
     */
    fun release(buffer: ByteBuffer) {
        if (buffer.capacity() == BufferSize.Write) {
            buffer.clear()
            stripe().push(buffer)
        }
    }

    private fun stripe(): Stripe = stripes[Thread.currentThread().threadId().toInt() and mask]

    private class Stripe {

        private val buffers = arrayOfNulls<ByteBuffer>(BuffersPerStripe)

        private var count = 0

        @Synchronized
        fun pop(): ByteBuffer? {
            if (count == 0) {
                return null
            }

            val buffer = buffers[--count]
            buffers[count] = null

            return buffer
        }

        @Synchronized
        fun push(buffer: ByteBuffer) {
            // dropped buffers are freed by the garbage collector
            if (count < BuffersPerStripe) {
                buffers[count++] = buffer
            }
        }

    }

}
