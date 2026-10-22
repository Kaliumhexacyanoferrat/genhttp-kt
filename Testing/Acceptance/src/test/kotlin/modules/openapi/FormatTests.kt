package org.genhttp.testing.acceptance.modules.openapi

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader

import kotlin.test.assertEquals

class FormatTests {

    // region Tests

    @MultiEngineTest
    fun testJsonFile(engine: ServerEngine) = test { testApi(engine, "/openapi.json", "application/json") }

    @MultiEngineTest
    fun testJsonFileUpper(engine: ServerEngine) = test { testApi(engine, "/OPENAPI.JSON", "application/json") }

    @MultiEngineTest
    fun testJsonFallback(engine: ServerEngine) = test { testApi(engine, "/openapi", "application/json") }

    @MultiEngineTest
    fun testYamlFile(engine: ServerEngine) = test { testApi(engine, "/openapi.yaml", "application/yaml") }

    @MultiEngineTest
    fun testYmlFile(engine: ServerEngine) = test { testApi(engine, "/openapi.yml", "application/yaml") }

    @MultiEngineTest
    fun testJsonByAccept(engine: ServerEngine) = test { testApi(engine, "/openapi", accept = "application/json") }

    @MultiEngineTest
    fun testJsonByAcceptUpper(engine: ServerEngine) = test { testApi(engine, "/OPENAPI", accept = "application/json") }

    @MultiEngineTest
    fun testYamlByAccept(engine: ServerEngine) = test { testApi(engine, "/openapi", accept = "application/yaml") }

    @MultiEngineTest
    fun testUnsupportedFormat(engine: ServerEngine) = test {
        TestHost.run(getApi(), engine = engine).use { host ->
            val request = host.getRequest("/openapi")

            request.header("Accept", "text/plain")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    // endregion

    // region Helpers

    private suspend fun testApi(engine: ServerEngine, path: String, expectedContentType: String? = null, accept: String? = null) {
        TestHost.run(getApi(), engine = engine).use { host ->
            val request = host.getRequest(path)

            if (accept != null) {
                request.header("Accept", accept)
            }

            val response = host.getResponse(request)

            assertEquals(expectedContentType ?: accept, response.getContentHeader("Content-Type"))

            response.asOpenApi()
        }
    }

    private fun getApi(): InlineBuilder {
        return Inline.create()
            .get { "Hello World" }
            .add(ApiDescription.create())
    }

    // endregion

}
