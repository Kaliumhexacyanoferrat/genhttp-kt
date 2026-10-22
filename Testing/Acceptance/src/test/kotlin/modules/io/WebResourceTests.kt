package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Asset
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.fromWeb
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import org.junit.jupiter.api.Test

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebResourceTests {

    @MultiEngineTest
    fun testLifecycle(engine: ServerEngine) = test {
        val port = TestHost.nextPort()

        val resource = Resource.fromWeb("http://localhost:$port/source")

        val source = Content.from(Resource.fromString("Hello World"))

        val target = Content.from(resource)

        val response = run(source, target, engine, port)

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Hello World", response.getContent())
    }

    @MultiEngineTest
    fun testOverwrites(engine: ServerEngine) = test {
        val port = TestHost.nextPort()

        val resource = Resource.fromWeb(URI("http://localhost:$port/source"))
            .modified(Instant.parse("2005-12-03T12:00:00Z"))
            .type(ContentType.ApplicationJson)
            .name("Test.txt")

        val source = Content.from(Resource.fromString("Hello World"))

        val target = Asset.from(resource)
            .asDownload()

        val response = run(source, target, engine, port)

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("application/json", response.getContentHeader("Content-Type"))
        assertEquals("attachment; filename=\"Test.txt\"", response.getContentHeader("Content-Disposition"))
    }

    @Test
    fun testBuilderDoesRequireHttp() {
        assertFailsWith<IllegalStateException> { Resource.fromWeb("file:///test.txt").build() }
    }

    @Test
    fun testBuilderDoesRequireAbsolute() {
        assertFailsWith<IllegalArgumentException> { Resource.fromWeb("./test.txt").build() }
    }

    @Test
    fun testBuilderDoesRequireUri() {
        assertFailsWith<IllegalArgumentException> { Resource.fromWeb("http://").build() }
    }

    private suspend fun run(source: HandlerBuilder, target: HandlerBuilder, engine: ServerEngine, port: Int): HttpResponse<ByteArray> {
        val app = Layout.create()
            .add("source", source)
            .add("target", target)

        TestHost(app.build(), engine = engine).use { runner ->
            runner.host.port(port).handler(app)

            runner.start()

            return runner.getResponse(HttpRequest.newBuilder(URI.create("http://localhost:$port/target")).GET().build())
        }
    }

}
