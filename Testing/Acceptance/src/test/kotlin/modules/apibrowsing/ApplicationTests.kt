package org.genhttp.testing.acceptance.modules.apibrowsing

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.apibrowsing.addRedoc
import org.genhttp.modules.apibrowsing.addScalar
import org.genhttp.modules.apibrowsing.addSwaggerUi
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.addOpenApi
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import kotlin.test.assertEquals

class ApplicationTests {

    @MultiEngineTest
    fun testSwagger(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Inline.create().get { 42 })
            .addOpenApi()
            .addSwaggerUi()

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/swagger/")

            response.assertStatus(ResponseStatus.Ok)

            AssertX.contains("Swagger", response.getContent())
        }
    }

    @MultiEngineTest
    fun testRedoc(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Inline.create().get { 42 })
            .addOpenApi()
            .addRedoc()

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/redoc/")

            response.assertStatus(ResponseStatus.Ok)

            AssertX.contains("Redoc", response.getContent())
        }
    }

    @MultiEngineTest
    fun testScalar(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Inline.create().get { 42 })
            .addOpenApi()
            .addScalar()

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/scalar/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("text/html", response.getContentHeader("Content-Type")?.substringBefore(';')?.trim())

            AssertX.contains("Scalar", response.getContent())
        }
    }

}
