package org.genhttp.testing.acceptance.modules.openapi

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.modules.functional.*
import org.genhttp.modules.redirects.Redirect
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.io.ByteArrayInputStream

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseTests {

    // region Tests

    @MultiEngineTest
    fun testFormatted(engine: ServerEngine) = test {
        val api = Inline.create().get { 42 }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("200"))
        assertEquals(true, op.responses?.getValue("200")?.content?.containsKey("text/plain"))
    }

    @MultiEngineTest
    fun testFormattedNullable(engine: ServerEngine) = test {
        val api = Inline.create().get<Int?> { 42 }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("204"))
    }

    @MultiEngineTest
    fun testStream(engine: ServerEngine) = test {
        val api = Inline.create().get { ByteArrayInputStream(ByteArray(0)) }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("200"))
        assertEquals(true, op.responses?.getValue("200")?.content?.containsKey("application/octet-stream"))

        assertEquals("binary", op.responses?.getValue("200")?.content?.getValue("application/octet-stream")?.schema?.format)
    }

    @MultiEngineTest
    fun testNone(engine: ServerEngine) = test {
        val api = Inline.create().get { }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(false, op.responses?.containsKey("200"))
        assertEquals(true, op.responses?.containsKey("204"))
    }

    @MultiEngineTest
    fun testDynamic(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("h") { parent: Handler -> Redirect.to("https://google.de").build() }
            .get("hb") { Redirect.to("https://google.de") }
            .get("r") { request: Request -> request.respond().build() }
            .get("rb") { request: Request -> request.respond() }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("200"))
        assertEquals(true, op.responses?.getValue("200")?.content?.containsKey("*/*"))
    }

    @MultiEngineTest
    fun testSerialized(engine: ServerEngine) = test {
        val api = Inline.create().get { HashSet<Int>() }

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("200"))
        assertEquals(true, op.responses?.getValue("200")?.content?.containsKey("application/json"))
    }

    // endregion

}
