package org.genhttp.modules.mcp.protocol

import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

import java.io.ByteArrayOutputStream

/**
 * A growable buffer with a JSON generator to write protocol messages with.
 *
 * Upstream caches one buffer per thread so that writing a response does not allocate apart from the final
 * result. Coroutines are not bound to a thread (the internal engine dispatches them to new virtual threads), so
 * a buffer is allocated per message instead.
 *
 * Answers are JSON for a machine to read, never embedded in HTML: neither HTML-sensitive nor non-ASCII
 * characters are escaped.
 */
internal class JsonBuffer private constructor() : AutoCloseable {

    private val output = ByteArrayOutputStream(InitialSize)

    /**
     * The generator writing into this buffer.
     */
    val writer: JsonGenerator = Mapper.createGenerator(output)

    // region Functionality

    /**
     * Flushes the generator and returns the JSON written so far.
     */
    fun flush(): String {
        writer.flush()
        return output.toString(Charsets.UTF_8)
    }

    /**
     * Flushes the generator and returns the UTF-8 encoded JSON written so far.
     */
    fun toArray(): ByteArray {
        writer.flush()
        return output.toByteArray()
    }

    override fun close() {
        writer.close()
    }

    // endregion

    companion object {

        private const val InitialSize = 4096

        /**
         * Parses requests and writes messages (no escaping beyond what JSON requires, nesting limited to 64 levels).
         */
        val Mapper: JsonMapper = JsonMapper.builder(
            JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).build())
                .build()
        )
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

        /**
         * Provides a buffer to write a message into.
         */
        fun rent(): JsonBuffer = JsonBuffer()

    }

}
