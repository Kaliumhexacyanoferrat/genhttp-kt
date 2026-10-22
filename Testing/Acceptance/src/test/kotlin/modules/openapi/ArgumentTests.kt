package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.oas.models.Operation

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.modules.reflection.FromBody
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.io.InputStream
import java.time.LocalDate

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArgumentTests {

    // region Tests

    @MultiEngineTest
    fun testInjectionCausesNoArgument(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { request: Request -> request.header.headers.getEntry("Host") }

        val (_, op) = Extensions.getOperation(engine, api)

        AssertX.empty(op.parameters)
    }

    @MultiEngineTest
    fun testPathParam(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/users/:id") { id: Int -> id }

        val (path, operation) = Extensions.getOperation(engine, api)

        assertEquals("/users/{id}", path.first)
        assertParameter(operation, "id", "path")
    }

    @MultiEngineTest
    fun testQueryParam(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/users/") { id: Int -> id }

        val (path, operation) = Extensions.getOperation(engine, api)

        assertEquals("/users/", path.first)
        assertParameter(operation, "id", "query")
    }

    @MultiEngineTest
    fun testBodyParam(engine: ServerEngine) = test {
        val api = Inline.create()
            .post("/users/filter") { items: HashSet<Int> -> items.size }

        val (path, operation) = Extensions.getOperation(engine, api)

        assertEquals("/users/filter", path.first)

        assertTrue(operation.requestBody?.content?.containsKey("application/json") ?: false)
        assertTrue(operation.requestBody?.content?.containsKey("text/xml") ?: false)
    }

    @MultiEngineTest
    fun testContentParam(engine: ServerEngine) = test {
        val api = Inline.create()
            .post("/users/filter", this@ArgumentTests::contentParam)

        val (path, operation) = Extensions.getOperation(engine, api)

        assertEquals("/users/filter", path.first)

        assertTrue(operation.requestBody?.content?.containsKey("text/plain") ?: false)
    }

    @MultiEngineTest
    fun testStreamParam(engine: ServerEngine) = test {
        val api = Inline.create()
            .put("/users/avatar") { file: InputStream -> true }

        val (path, operation) = Extensions.getOperation(engine, api)

        assertEquals("/users/avatar", path.first)

        assertTrue(operation.requestBody?.content?.containsKey("*/*") ?: false)
        assertEquals("binary", operation.requestBody?.content?.getValue("*/*")?.schema?.format)
    }

    @MultiEngineTest
    fun testWildcardParameter(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/files/:tenant/") { Content.from(Resource.fromString("File Content")) }

        val (path, _) = Extensions.getOperation(engine, api)

        assertEquals("/files/{tenant}/{remainingPath}", path.first)
    }

    // endregion

    // region Handlers (lambdas cannot be annotated, so they are declared as functions)

    fun contentParam(@FromBody date: LocalDate): LocalDate = date

    // endregion

    // region Helpers

    private fun assertParameter(operation: Operation, name: String, location: String) {
        assertEquals(1, operation.parameters?.size)

        val param = operation.parameters?.firstOrNull()

        assertNotNull(param)

        assertEquals(name, param.name)
        assertEquals(location, param.`in`)
    }

    // endregion

}
