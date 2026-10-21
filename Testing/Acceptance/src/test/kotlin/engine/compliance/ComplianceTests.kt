package org.genhttp.testing.acceptance.engine.compliance

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.engine.WireTest
import org.genhttp.testing.acceptance.test

import java.io.ByteArrayInputStream
import java.io.InputStream

class ComplianceTests : WireTest() {

    @MultiEngineTest
    fun testNoMinorHttpVersion(engine: ServerEngine) = test {
        assertWire(listOf("GET / HTTP/1", "Host: localhost"), "505", Layout.create(), engine)
    }

    @MultiEngineTest
    fun testNonSupportedVersion(engine: ServerEngine) = test {
        assertWire(listOf("GET / HTTP/1.2", "Host: localhost"), "505", Layout.create(), engine)
    }

    @MultiEngineTest
    fun testEmptyHost(engine: ServerEngine) = test {
        assertWire(listOf("GET / HTTP/1.0", "Host: "), "400", Layout.create(), engine)
    }

    @MultiEngineTest
    fun testPostNoBody(engine: ServerEngine) = test {
        assertWire(listOf("POST / HTTP/1.0", "Host: host"), "404", Layout.create(), engine)
    }

    @MultiEngineTest
    fun testGetWithBody(engine: ServerEngine) = test {
        assertWire(listOf("GET / HTTP/1.0", "Host: host", "Content-Length: 3", "", "123"), "400", Layout.create(), engine)
    }

    @MultiEngineTest
    fun testChunkWithSemicolon(engine: ServerEngine) = test {
        val handler = Inline.create().post { body: InputStream ->
            ByteArrayInputStream(body.readAllBytes())
        }

        assertWire(listOf("POST / HTTP/1.0", "Host: host", "Transfer-Encoding: chunked", "", "5;", "hello", "", "0", ""), "400", handler, engine)
    }

}
