package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.addRangeSupport
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.openapi.ApiDiscovery
import org.genhttp.modules.openapi.ApiExplorer
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistry
import org.genhttp.modules.openapi.discovery.InheritedDocumentation
import org.genhttp.modules.openapi.discovery.SchemaManager
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryTests {

    // region Tests

    @MultiEngineTest
    fun testTraversal(engine: ServerEngine) = test {
        val api = Layout.create()
            .add("service", Layout.create().add(Inline.create().get("/method") { 1 }))
            .add(Inline.create().get("/method2") { 2 })
            .addRangeSupport()
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertTrue(doc?.paths?.containsKey("/service/method") ?: false)
        assertTrue(doc.paths.containsKey("/method2"))
    }

    @MultiEngineTest
    fun testCustomExplorer(engine: ServerEngine) = test {
        val discovery = ApiDiscovery.empty().add<CustomExplorer>()

        val api = Inline.create().add(ApiDescription.with(discovery))

        val doc = api.getOpenApi(engine, false).document

        assertEquals("Added by explorer", doc?.servers?.first()?.description)
    }

    @MultiEngineTest
    fun testSamePathWithDifferentMethods(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/method") { 42 }
            .put("/method") { i: Int -> i }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        assertEquals(1, doc.paths?.size ?: 0)

        val operations = doc.paths?.entries?.first()?.value?.readOperationsMap()

        assertEquals(2, operations?.size ?: 0)

        assertTrue(operations?.containsKey(PathItem.HttpMethod.GET) ?: false)
        assertTrue(operations.containsKey(PathItem.HttpMethod.PUT))
    }

    // endregion

    // region Supporting data structures

    class CustomExplorer : ApiExplorer {

        override fun canExplore(handler: Handler): Boolean = true

        override suspend fun explore(
            request: Request,
            handler: Handler,
            path: List<String>,
            document: OpenAPI,
            schemata: SchemaManager,
            documentation: InheritedDocumentation,
            registry: ApiDiscoveryRegistry
        ) {
            document.servers.first().description = "Added by explorer"
        }

    }

    // endregion

}
