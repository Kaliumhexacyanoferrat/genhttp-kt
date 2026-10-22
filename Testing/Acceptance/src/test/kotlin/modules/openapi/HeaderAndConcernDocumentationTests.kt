package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.services.ApiRequestHeader
import org.genhttp.api.content.services.ApiResponse
import org.genhttp.api.content.services.ApiResponseHeader
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.clientcaching.ClientCache
import org.genhttp.modules.compression.DecompressedContent
import org.genhttp.modules.functional.*
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.modules.i18n.Localization
import org.genhttp.modules.io.RangeSupport
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HeaderAndConcernDocumentationTests {

    // region Request headers

    @MultiEngineTest
    fun testRequestHeader(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::requestHeader)

        val (_, op) = Extensions.getOperation(engine, api)

        val header = op.parameters?.first { it.name == "X-Page" }

        assertEquals("header", header?.`in`)
        assertEquals("The page to return", header?.description)
        assertEquals(true, header?.required)
        assertEquals("integer", header?.schema?.type)
    }

    @MultiEngineTest
    fun testOptionalStringRequestHeader(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::optionalRequestHeader)

        val (_, op) = Extensions.getOperation(engine, api)

        val header = op.parameters?.first { it.name == "X-Request-Id" }

        assertEquals(false, header?.required)
        assertEquals("string", header?.schema?.type)
    }

    @MultiEngineTest
    fun testServiceRequestHeaders(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val get = doc.paths.getValue("/service/").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        val parameters = get?.parameters?.filter { it.`in` == "header" }

        assertEquals(2, parameters?.size)
        assertEquals("Overridden by the method", parameters?.first { it.name == "X-Tenant" }?.description)
        assertEquals("Correlates the request", parameters?.first { it.name == "X-Request-Id" }?.description)
    }

    // endregion

    // region Response headers

    @MultiEngineTest
    fun testResponseHeaderOnSuccess(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::responseHeaderOnSuccess)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The suggested file name", op.responses?.getValue("200")?.headers?.getValue("Content-Disposition")?.description)

        assertFalse(op.responses?.getValue("404")?.headers?.containsKey("Content-Disposition") ?: false)
    }

    @MultiEngineTest
    fun testResponseHeaderOnStatus(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::responseHeaderOnStatus)

        val (_, op) = Extensions.getOperation(engine, api)

        val header = op.responses?.getValue("429")?.headers?.getValue("Retry-After")

        assertEquals("Seconds to wait", header?.description)
        assertEquals("integer", header?.schema?.type)

        assertFalse(op.responses?.getValue("200")?.headers?.containsKey("Retry-After") ?: false)
    }

    @MultiEngineTest
    fun testResponseHeaderRequiresResponse(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::responseHeaderRequiresResponse)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(false, op.responses?.containsKey("304"))
    }

    @MultiEngineTest
    fun testServiceResponseHeaders(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val headers = doc.paths.getValue("/service/").readOperationsMap().getValue(PathItem.HttpMethod.GET).responses?.getValue("200")?.headers

        assertEquals(2, headers?.size)
        assertEquals("Specific to the method", headers?.getValue("X-Version")?.description)
        assertEquals("The server time", headers?.getValue("X-Time")?.description)
    }

    private suspend fun getServiceDocument(engine: ServerEngine): OpenAPI {
        val api = Layout.create()
            .addService<HeaderService>("service")
            .add(ApiDescription.create())

        return api.getOpenApi(engine).document!!
    }

    // endregion

    // region Concerns

    @MultiEngineTest
    fun testConcernDocumentationIsApplied(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/a") { 42 }
            .post("/b") { 42 }
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiRequestHeader("Authorization", "The token", required = true),
                        ApiResponse(ResponseStatus.Unauthorized, "No token given"),
                        ApiResponseHeader("WWW-Authenticate", "The supported scheme", arrayOf(ResponseStatus.Unauthorized))
                    )
                )
            )

        val doc = getDocument(api, engine)

        for (op in getOperations(doc)) {
            assertEquals(true, op.parameters?.any { it.name == "Authorization" && it.`in` == "header" && it.required == true })

            assertEquals("No token given", op.responses?.getValue("401")?.description)
            assertEquals("The supported scheme", op.responses?.getValue("401")?.headers?.getValue("WWW-Authenticate")?.description)

            assertEquals(true, op.responses?.containsKey("200"))
        }
    }

    @MultiEngineTest
    fun testOperationOverridesConcern(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@HeaderAndConcernDocumentationTests::overriddenByOperation)
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiResponse(ResponseStatus.Unauthorized, "Declared by the concern")
                    )
                )
            )

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("Declared by the operation", op.responses?.getValue("401")?.description)
    }

    @MultiEngineTest
    fun testServiceOverridesConcern(engine: ServerEngine) = test {
        val api = Layout.create()
            .addService<HeaderService>("service")
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiRequestHeader("X-Request-Id", "Declared by the concern"),
                        ApiResponseHeader("X-Time", "Declared by the concern")
                    )
                )
            )

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("Correlates the request", op.parameters?.first { it.name == "X-Request-Id" }?.description)
        assertEquals("The server time", op.responses?.getValue("200")?.headers?.getValue("X-Time")?.description)
    }

    @MultiEngineTest
    fun testInnerConcernOverridesOuter(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiResponse(ResponseStatus.Forbidden, "Inner"),
                        ApiRequestHeader("X-Inner")
                    )
                )
            )
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiResponse(ResponseStatus.Forbidden, "Outer"),
                        ApiResponse(ResponseStatus.Unauthorized, "Outer"),
                        ApiRequestHeader("X-Outer")
                    )
                )
            )

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("Inner", op.responses?.getValue("403")?.description)
        assertEquals("Outer", op.responses?.getValue("401")?.description)

        assertEquals(true, op.parameters?.any { it.name == "X-Inner" })
        assertEquals(true, op.parameters?.any { it.name == "X-Outer" })
    }

    @MultiEngineTest
    fun testConcernResponsesAreMerged(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .add(
                DocumentedConcernBuilder(
                    listOf(
                        ApiResponse(ResponseStatus.BadRequest, "The error", contentType = "application/problem+json"),
                        ApiResponse(ResponseStatus.BadRequest, contentType = "text/plain")
                    )
                )
            )

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("The error", op.responses?.getValue("400")?.description)
        assertEquals(2, op.responses?.getValue("400")?.content?.size)
    }

    @MultiEngineTest
    fun testConcernOnlyAppliesToContent(engine: ServerEngine) = test {
        val api = Layout.create()
            .add("documented", Inline.create().get { 42 }.add(DocumentedConcernBuilder(listOf(ApiRequestHeader("X-Documented")))))
            .add("plain", Inline.create().get { 42 })

        val doc = getDocument(api, engine)

        assertEquals(true, doc.paths.getValue("/documented/").readOperationsMap().getValue(PathItem.HttpMethod.GET).parameters?.any { it.name == "X-Documented" })
        assertFalse(doc.paths.getValue("/plain/").readOperationsMap().getValue(PathItem.HttpMethod.GET).parameters?.any { it.name == "X-Documented" } ?: false)
    }

    @MultiEngineTest
    fun testConcernDecidesPerOperation(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/a") { 42 }
            .post("/a") { 42 }
            .add(DocumentedConcernBuilder(listOf(ApiRequestHeader("X-Read-Only")), RequestMethod.Get))

        val doc = getDocument(api, engine)

        val operations = doc.paths.getValue("/a").readOperationsMap()

        assertEquals(true, operations.getValue(PathItem.HttpMethod.GET).parameters?.any { it.name == "X-Read-Only" })
        assertFalse(operations.getValue(PathItem.HttpMethod.POST).parameters?.any { it.name == "X-Read-Only" } ?: false)
    }

    @MultiEngineTest
    fun testEmptyConcernDocumentation(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .add(DocumentedConcernBuilder(emptyList()))

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals(true, op.responses?.containsKey("200"))
    }

    private suspend fun getDocument(api: InlineBuilder, engine: ServerEngine): OpenAPI =
        api.add(ApiDescription.create()).getOpenApi(engine).document!!

    private suspend fun getDocument(api: LayoutBuilder, engine: ServerEngine): OpenAPI =
        api.add(ApiDescription.create()).getOpenApi(engine).document!!

    private fun getOperations(doc: OpenAPI): List<Operation> =
        doc.paths.values.flatMap { it.readOperationsMap()?.values ?: emptyList() }

    // endregion

    // region Built-in concerns

    @MultiEngineTest
    fun testCacheValidation(engine: ServerEngine) = test {
        val (get, post) = getBuiltInOperations(ClientCache.validation(), engine)

        assertEquals(true, get.parameters?.any { it.name == "If-None-Match" && it.`in` == "header" })
        assertEquals(true, get.responses?.getValue("200")?.headers?.containsKey("ETag"))
        assertEquals(true, get.responses?.getValue("304")?.headers?.containsKey("ETag"))

        assertFalse(post.parameters?.any { it.name == "If-None-Match" } ?: false)
        assertEquals(false, post.responses?.containsKey("304"))
    }

    @MultiEngineTest
    fun testCachePolicy(engine: ServerEngine) = test {
        val (get, post) = getBuiltInOperations(ClientCache.policy().duration(1), engine)

        assertEquals(true, get.responses?.getValue("200")?.headers?.containsKey("Expires"))
        assertFalse(post.responses?.getValue("200")?.headers?.containsKey("Expires") ?: false)
    }

    @MultiEngineTest
    fun testRangeSupport(engine: ServerEngine) = test {
        val (get, post) = getBuiltInOperations(RangeSupport.create(), engine)

        assertEquals(true, get.parameters?.any { it.name == "Range" && it.`in` == "header" })
        assertEquals(true, get.responses?.getValue("200")?.headers?.containsKey("Accept-Ranges"))
        assertEquals(true, get.responses?.getValue("206")?.headers?.containsKey("Content-Range"))
        assertEquals(true, get.responses?.getValue("416")?.headers?.containsKey("Content-Range"))
        assertEquals(true, get.responses?.getValue("416")?.content?.containsKey("text/plain"))

        assertEquals(false, post.responses?.containsKey("206"))
    }

    @MultiEngineTest
    fun testLocalization(engine: ServerEngine) = test {
        val (get, post) = getBuiltInOperations(Localization.create(), engine)

        assertEquals(true, get.responses?.getValue("200")?.headers?.containsKey("Content-Language"))
        assertEquals(true, post.responses?.getValue("200")?.headers?.containsKey("Content-Language"))
    }

    @MultiEngineTest
    fun testDecompression(engine: ServerEngine) = test {
        val (get, post) = getBuiltInOperations(DecompressedContent.default(), engine)

        val header = post.parameters?.first { it.name == "Content-Encoding" }

        assertEquals("header", header?.`in`)
        AssertX.contains("gzip", header?.description)

        assertFalse(get.parameters?.any { it.name == "Content-Encoding" } ?: false)
    }

    private suspend fun getBuiltInOperations(concern: ConcernBuilder, engine: ServerEngine): Pair<Operation, Operation> {
        val api = Inline.create()
            .get("/a") { 42 }
            .post("/a") { 42 }
            .add(concern)

        val operations = getDocument(api, engine).paths.getValue("/a").readOperationsMap()

        return Pair(operations.getValue(PathItem.HttpMethod.GET), operations.getValue(PathItem.HttpMethod.POST))
    }

    // endregion

    // region Handlers (lambdas cannot be annotated, so they are declared as functions)

    @ApiRequestHeader("X-Page", "The page to return", required = true, type = Int::class)
    fun requestHeader(request: Request): Int = 42

    @ApiRequestHeader("X-Request-Id")
    fun optionalRequestHeader(request: Request): Int = 42

    @ApiResponseHeader("Content-Disposition", "The suggested file name")
    @ApiResponse(ResponseStatus.NotFound, "Nothing here")
    fun responseHeaderOnSuccess(): Int = 42

    @ApiResponse(ResponseStatus.TooManyRequests, "Slow down")
    @ApiResponseHeader("Retry-After", "Seconds to wait", status = [ResponseStatus.TooManyRequests], type = Int::class)
    fun responseHeaderOnStatus(): Int = 42

    @ApiResponseHeader("ETag", status = [ResponseStatus.NotModified])
    fun responseHeaderRequiresResponse(): Int = 42

    @ApiResponse(ResponseStatus.Unauthorized, "Declared by the operation")
    fun overriddenByOperation(): Int = 42

    // endregion

    // region Supporting data structures

    @ApiRequestHeader("X-Tenant", "Declared by the service")
    @ApiRequestHeader("X-Request-Id", "Correlates the request")
    @ApiResponseHeader("X-Version", "Declared by the service")
    @ApiResponseHeader("X-Time", "The server time")
    class HeaderService {

        @ResourceMethod
        @ApiRequestHeader("X-Tenant", "Overridden by the method")
        @ApiResponseHeader("X-Version", "Specific to the method")
        fun get(): Int = 42

    }

    private class DocumentedConcernBuilder(private val documentation: List<Annotation>, private val method: RequestMethod? = null) : ConcernBuilder {

        override fun build(content: Handler): Concern = DocumentedConcern(content, documentation, method)

    }

    private class DocumentedConcern(override val content: Handler, private val documentation: List<Annotation>, private val method: RequestMethod?) :
        org.genhttp.api.content.services.DocumentedConcern {

        override fun addDocumentation(operation: OperationDocumentation) {
            if (method == null || operation.method == method) {
                for (entry in documentation) {
                    operation.add(entry)
                }
            }
        }

        override suspend fun prepare(server: Server) = content.prepare(server)

        override suspend fun handle(request: Request): Response? = content.handle(request)

    }

    // endregion

}
