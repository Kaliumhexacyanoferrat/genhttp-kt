package org.genhttp.modules.io

import org.genhttp.api.protocol.KeyValueList
import org.genhttp.api.protocol.PercentEncoding
import org.genhttp.api.protocol.RequestBody

import java.nio.ByteBuffer

/**
 * Provides access to the key/value pairs of a request body that
 * has been encoded as "application/x-www-form-urlencoded".
 */
public class BodyArguments private constructor(private val entries: List<Pair<ByteBuffer, ByteBuffer>>) : KeyValueList {

    override val count: Int
        get() = entries.size

    override fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer> {
        val entry = entries[index]

        return Pair(entry.first.duplicate(), entry.second.duplicate())
    }

    public companion object {

        /**
         * A body that does not contain any arguments.
         */
        public val Empty: BodyArguments = BodyArguments(emptyList())

        /**
         * Reads and parses the given body as form encoded content.
         *
         * @param body The body to be parsed
         * @return The arguments found within the body
         */
        public suspend fun create(body: RequestBody): BodyArguments {
            val memory = body.asMemory()

            return parse(memory)
        }

        private fun parse(memory: ByteBuffer): BodyArguments {
            if (!memory.hasRemaining()) {
                return Empty
            }

            val entries = mutableListOf<Pair<ByteBuffer, ByteBuffer>>()

            var start = memory.position()
            val end = memory.limit()

            while (start < end) {
                val separator = indexOf(memory, '&'.code.toByte(), start, end)

                val pairEnd = if (separator < 0) end else separator

                if (pairEnd > start) {
                    entries.add(parsePair(memory.slice(start, pairEnd - start)))
                }

                start = if (separator < 0) end else separator + 1
            }

            return BodyArguments(entries)
        }

        private fun parsePair(pair: ByteBuffer): Pair<ByteBuffer, ByteBuffer> {
            val start = pair.position()
            val end = pair.limit()

            val separator = indexOf(pair, '='.code.toByte(), start, end)

            return if (separator < 0) {
                Pair(decode(pair), ByteBuffer.allocate(0).asReadOnlyBuffer())
            } else {
                Pair(decode(pair.slice(start, separator - start)), decode(pair.slice(separator + 1, end - separator - 1)))
            }
        }

        private fun indexOf(buffer: ByteBuffer, value: Byte, start: Int, end: Int): Int {
            for (i in start until end) {
                if (buffer.get(i) == value) {
                    return i
                }
            }

            return -1
        }

        private fun decode(raw: ByteBuffer): ByteBuffer = PercentEncoding.decode(raw, decodePlus = true)

    }

}
