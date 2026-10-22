package org.genhttp.testing.acceptance.modules.openapi

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.functional.*
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.openapi.handler.OpenApiConcernBuilder
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals

class MetaDataTests {

    // region Tests

    @MultiEngineTest
    fun testDefaultTitleAndVersion(engine: ServerEngine) = test {
        val doc = getApi(ApiDescription.create()).getOpenApi(engine)

        assertEquals("Swagger specification", doc.document?.info?.title)
        assertEquals("1.0.0", doc.document?.info?.version)
    }

    @MultiEngineTest
    fun testCustomTitleAndVersion(engine: ServerEngine) = test {
        val desc = ApiDescription.create()
            .title("My Title")
            .version("2.0.1")

        val doc = getApi(desc).getOpenApi(engine)

        assertEquals("My Title", doc.document?.info?.title)
        assertEquals("2.0.1", doc.document?.info?.version)
    }

    @MultiEngineTest
    fun testServerGenerated(engine: ServerEngine) = test {
        val doc = getApi(ApiDescription.create()).getOpenApi(engine)

        val server = doc.document?.servers?.first()

        AssertX.contains("http://localhost:", server?.url)
    }

    @MultiEngineTest
    fun testPostProcessing(engine: ServerEngine) = test {
        val desc = ApiDescription.create()
            .postProcessor { _, doc -> doc.servers.first().url = "https://google.de/" }

        val doc = getApi(desc).getOpenApi(engine)

        assertEquals("https://google.de/", doc.document?.servers?.first()?.url)
    }

    // endregion

    // region Helpers

    private fun getApi(description: OpenApiConcernBuilder): InlineBuilder {
        return Inline.create()
            .get { "Hello World" }
            .add(description)
    }

    // endregion

}
