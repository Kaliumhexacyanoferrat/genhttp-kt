package org.genhttp.modules.io.streaming

/**
 * Pools the byte arrays used to transfer content (the counterpart of `ArrayPool<byte>.Shared` used by GenHTTP).
 *
 * Arrays are pooled in power of two sizes from 4 KB to 64 KB, larger arrays are neither pooled nor reused.
 * The pool is split into stripes (selected by the current thread) to avoid contention.
 */
internal object BytePool {

    private const val MinShift = 12

    private const val MaxShift = 16

    private const val ArraysPerSize = 2

    private val stripes = Array(Integer.highestOneBit(Runtime.getRuntime().availableProcessors()) * 2) { Stripe() }

    private val mask = stripes.size - 1

    /**
     * Returns an array of at least the given size, which should be passed to [release] when it is no longer used.
     */
    fun rent(minimumSize: Int): ByteArray {
        val shift = shiftOf(minimumSize) ?: return ByteArray(minimumSize)

        return stripe().pop(shift - MinShift) ?: ByteArray(1 shl shift)
    }

    /**
     * Hands the given array back to the pool, the caller must not use it anymore.
     */
    fun release(array: ByteArray) {
        val shift = shiftOf(array.size) ?: return

        if (array.size == 1 shl shift) {
            stripe().push(shift - MinShift, array)
        }
    }

    private fun shiftOf(size: Int): Int? {
        if (size <= 1 shl MinShift) {
            return MinShift
        }

        if (size > 1 shl MaxShift) {
            return null
        }

        return 32 - Integer.numberOfLeadingZeros(size - 1)
    }

    private fun stripe(): Stripe = stripes[Thread.currentThread().threadId().toInt() and mask]

    private class Stripe {

        private val arrays = Array(MaxShift - MinShift + 1) { arrayOfNulls<ByteArray>(ArraysPerSize) }

        private val counts = IntArray(MaxShift - MinShift + 1)

        @Synchronized
        fun pop(bucket: Int): ByteArray? {
            val count = counts[bucket]

            if (count == 0) {
                return null
            }

            val array = arrays[bucket][count - 1]

            arrays[bucket][count - 1] = null
            counts[bucket] = count - 1

            return array
        }

        @Synchronized
        fun push(bucket: Int, array: ByteArray) {
            val count = counts[bucket]

            // arrays that do not fit are left to the garbage collector
            if (count < ArraysPerSize) {
                arrays[bucket][count] = array
                counts[bucket] = count + 1
            }
        }

    }

}
