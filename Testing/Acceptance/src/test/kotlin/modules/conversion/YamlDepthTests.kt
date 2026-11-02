package org.genhttp.testing.acceptance.modules.conversion

import kotlinx.coroutines.runBlocking

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.serializers.yaml.YamlFormat
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import tools.jackson.core.JacksonException

import java.io.ByteArrayInputStream
import java.net.http.HttpRequest.BodyPublishers

import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class YamlDepthTests {

    // region Supporting data structures

    class Node {

        var child: Node? = null

    }

    // endregion

    // region Tests

    @Test
    fun testDocumentAtMaximumDepthIsAccepted() {
        var node = deserialize(nest(MaximumDepth - 1))

        var depth = 0

        while (node != null) {
            depth++
            node = node.child
        }

        assertEquals(MaximumDepth, depth)
    }

    @Test
    fun testDocumentAboveMaximumDepthIsRejected() {
        assertFailsWith<JacksonException> { deserialize(nest(MaximumDepth)) }
    }

    @Test
    fun testDeeplyNestedDocumentIsRejected() {
        assertFailsWith<JacksonException> { deserialize(nest(100)) }
    }

    @Test
    fun testVeryDeeplyNestedDocumentIsRejected() {
        assertFailsWith<JacksonException> { deserialize(nest(100_000)) }
    }

    @Test
    fun testDeeplyNestedSequenceIsRejected() {
        val yaml = "[".repeat(100_000) + "]".repeat(100_000)

        assertFailsWith<JacksonException> { deserialize(yaml) }
    }

    @Test
    fun testUnknownPropertyAtMaximumDepthIsRejected() {
        assertFailsWith<JacksonException> { deserialize(nest(MaximumDepth - 1, "unknown: 1")) }
    }

    @MultiEngineTest
    fun testDeeplyNestedBodyReturnsBadRequest(engine: ServerEngine) = test {
        val inline = Inline.create()
            .post("/t") { node: Node -> node.child != null }

        TestHost.run(inline, engine = engine).use { runner ->
            val request = runner.getRequest("/t")
                .method("POST", BodyPublishers.ofString(nest(100_000)))
                .header("Content-Type", "application/yaml")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    // endregion

    // region Helpers

    private fun nest(depth: Int, leaf: String = "child: null"): String =
        "child: {".repeat(depth) + leaf + "}".repeat(depth)

    /**
     * Deserializes the given document on a thread with a small stack (like upstream), so that a recursive parser
     * would fail with a stack overflow.
     */
    private fun deserialize(yaml: String): Node? {
        var result: Node? = null
        var error: Throwable? = null

        val thread = Thread(null, {
            try {
                ByteArrayInputStream(yaml.toByteArray(Charsets.UTF_8)).use { stream ->
                    result = runBlocking { YamlFormat().deserialize(stream, typeOf<Node>()) } as Node?
                }
            } catch (e: Throwable) {
                error = e
            }
        }, "yaml-depth", StackSize)

        thread.start()
        thread.join()

        error?.let { throw it }

        return result
    }

    // endregion

    private companion object {

        const val MaximumDepth = 32

        const val StackSize = 1536L * 1024

    }

}
