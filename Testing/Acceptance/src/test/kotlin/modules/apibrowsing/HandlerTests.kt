package org.genhttp.testing.acceptance.modules.apibrowsing

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.apibrowsing.ApiBrowser
import org.genhttp.modules.apibrowsing.addSwaggerUi
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.openapi.addOpenApi
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import org.junit.jupiter.api.Test

import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals

class HandlerTests {

    @MultiEngineTest
    fun testGetOnly(engine: ServerEngine) = test {
        TestHost.run(getApi().addSwaggerUi(), engine = engine).use { host ->
            val request = host.getRequest("/swagger")
                .method("POST", BodyPublishers.ofString("Content"))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.MethodNotAllowed)

            assertEquals("GET", response.getContentHeader("Allow"))
        }
    }

    @MultiEngineTest
    fun testResourceAccess(engine: ServerEngine) = test {
        TestHost.run(getApi().addSwaggerUi(), engine = engine).use { host ->
            val response = host.getResponse("/swagger/static/swagger-ui.css")

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testNotFound(engine: ServerEngine) = test {
        TestHost.run(getApi().addSwaggerUi(), engine = engine).use { host ->
            val response = host.getResponse("/swagger/notfound")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testCustomMeta(engine: ServerEngine) = test {
        val api = getApi().addSwaggerUi("docs", "https://localhost:5001/swagger.json", "My API")

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse("/docs/")

            response.assertStatus(ResponseStatus.Ok)

            val content = response.getContent()

            AssertX.contains("https://localhost:5001/swagger.json", content)
            AssertX.contains("My API", content)
        }
    }

    @Test
    fun testConcernChaining() {
        Chain.works(ApiBrowser.redoc())
    }

    private fun getApi(): LayoutBuilder = Layout.create()
        .add(Inline.create().get { 42 })
        .addOpenApi()

}
