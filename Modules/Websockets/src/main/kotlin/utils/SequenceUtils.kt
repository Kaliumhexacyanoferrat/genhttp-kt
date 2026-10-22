package org.genhttp.modules.websockets.utils

import java.nio.ByteBuffer

/**
 * Helpers to work with fragmented data.
 */
public object SequenceUtils {

    /**
     * Concatenates the given segments into a single (read-only) buffer. The segments themselves are not modified.
     *
     * @param sequences The segments to be concatenated (null entries are skipped)
     * @return A buffer containing the data of all segments (empty, if there is no data)
     */
    public fun concatSequences(sequences: List<ByteBuffer?>?): ByteBuffer {
        if (sequences.isNullOrEmpty()) {
            return ByteBuffer.allocate(0).asReadOnlyBuffer()
        }

        var total = 0

        for (sequence in sequences) {
            if (sequence != null) {
                total += sequence.remaining()
            }
        }

        val result = ByteBuffer.allocate(total)

        for (sequence in sequences) {
            if (sequence != null) {
                result.put(sequence.duplicate())
            }
        }

        result.flip()

        return result.asReadOnlyBuffer()
    }

}
