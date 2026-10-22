package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema

import kotlinx.coroutines.yield

import org.genhttp.api.content.services.ApiRequestBody
import org.genhttp.api.content.services.ApiResponse
import org.genhttp.api.content.services.Remarks
import org.genhttp.api.content.services.Summary
import org.genhttp.api.content.services.Tag
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.reflection.FromBody
import org.genhttp.modules.reflection.Result
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.io.InputStream

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentationTests {

    // region Operations

    @MultiEngineTest
    fun testOperationIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::answer)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("Returns the answer", op.summary)
        assertEquals("To everything", op.description)
    }

    @MultiEngineTest
    fun testUndocumentedOperation(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }

        val (_, op) = Extensions.getOperation(engine, api)

        assertNull(op.summary)
        assertNull(op.description)
    }

    @MultiEngineTest
    fun testParametersAreDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/users/:id", this@DocumentationTests::describedParameters)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The ID of the user", op.parameters?.first { it.name == "id" }?.description)
        assertEquals("The fields to return", op.parameters?.first { it.name == "fields" }?.description)
    }

    @MultiEngineTest
    fun testResultIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::describedResult)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The answer", op.responses?.getValue("200")?.description)
    }

    @MultiEngineTest
    fun testDynamicResultIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::describedDynamicResult)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("Whatever happens", op.responses?.getValue("200")?.description)
    }

    // endregion

    // region Request bodies

    @MultiEngineTest
    fun testContentParameterIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::describedContent)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The user to create", op.requestBody?.description)
        assertEquals(true, op.requestBody?.content?.containsKey("application/json"))
    }

    @MultiEngineTest
    fun testBodyParameterIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::describedBody)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The text", op.requestBody?.description)
    }

    @MultiEngineTest
    fun testManuallyReadBody(engine: ServerEngine) = test {
        val api = Inline.create()
            .put(this@DocumentationTests::manuallyReadBody)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The content of the file", op.requestBody?.description)
        assertEquals(true, op.requestBody?.required)
        assertEquals("binary", op.requestBody?.content?.getValue("application/octet-stream")?.schema?.format)
    }

    @MultiEngineTest
    fun testStreamedBodyWithContentType(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::streamedBody)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The archive", op.requestBody?.description)
        assertEquals(1, op.requestBody?.content?.size)
        assertEquals("binary", op.requestBody?.content?.getValue("application/zip")?.schema?.format)
    }

    @MultiEngineTest
    fun testBodyWithMultipleContentTypes(engine: ServerEngine) = test {
        val api = Inline.create()
            .put(this@DocumentationTests::multipleBodies)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The picture", op.requestBody?.description)
        assertEquals(true, op.requestBody?.content?.containsKey("image/png"))
        assertEquals(true, op.requestBody?.content?.containsKey("image/jpeg"))
    }

    @MultiEngineTest
    fun testDeclaredBodyType(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredBodyType)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.requestBody?.content?.containsKey("application/json"))
        assertNotNull(op.requestBody?.content?.getValue("application/json")?.schema)
    }

    @MultiEngineTest
    fun testDeclaredDescriptionKeepsContent(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredDescription)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The user to create", op.requestBody?.description)
        assertEquals(true, op.requestBody?.content?.containsKey("application/json"))
        assertEquals(true, op.requestBody?.content?.containsKey("text/xml"))
    }

    @MultiEngineTest
    fun testDeclaredTextBody(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredTextBody)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("string", op.requestBody?.content?.getValue("text/plain; charset=utf-8")?.schema?.type)
    }

    @MultiEngineTest
    fun testDeclaredBodyIgnoredForGet(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::declaredBodyIgnoredForGet)

        val (_, op) = Extensions.getOperation(engine, api)

        assertNull(op.requestBody)
    }

    @MultiEngineTest
    fun testDeclaredBodyWithoutContent(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredBodyWithoutContent)

        val (_, op) = Extensions.getOperation(engine, api)

        assertNull(op.requestBody)
    }

    // endregion

    // region Responses

    @MultiEngineTest
    fun testDeclaredSuccessReplacesDerived(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredSuccess)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(false, op.responses?.containsKey("200"))
        assertEquals(false, op.responses?.containsKey("204"))

        assertEquals("The newly created user", op.responses?.getValue("201")?.description)
        assertEquals(true, op.responses?.getValue("201")?.content?.containsKey("application/json"))
    }

    @MultiEngineTest
    fun testDeclaredSuccessFallsBackToResultSummary(engine: ServerEngine) = test {
        val api = Inline.create()
            .post(this@DocumentationTests::declaredSuccessWithSummary)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("The accepted user", op.responses?.getValue("202")?.description)
    }

    @MultiEngineTest
    fun testErrorResponse(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/users/:id", this@DocumentationTests::errorResponse)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.containsKey("200"))

        assertEquals("There is no such user", op.responses?.getValue("404")?.description)
        assertEquals(true, op.responses?.getValue("404")?.content?.containsKey("application/json"))
    }

    @MultiEngineTest
    fun testErrorResponseWithoutContent(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::errorResponseWithoutContent)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("Too Many Requests", op.responses?.getValue("429")?.description)
        assertTrue(op.responses?.getValue("429")?.content == null || op.responses?.getValue("429")?.content?.size == 0)
    }

    @MultiEngineTest
    fun testNoContentDefaultDescription(engine: ServerEngine) = test {
        val api = Inline.create()
            .delete(this@DocumentationTests::noContent)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals("A response containing no body", op.responses?.getValue("204")?.description)
    }

    @MultiEngineTest
    fun testFormattableResponseType(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::formattableResponse)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(true, op.responses?.getValue("409")?.content?.containsKey("text/plain"))
    }

    @MultiEngineTest
    fun testDynamicResponseWithContentTypes(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::dynamicResponse)

        val (_, op) = Extensions.getOperation(engine, api)

        val response = op.responses?.getValue("200")

        assertEquals("The picture", response?.description)

        assertEquals(2, response?.content?.size)
        assertEquals("binary", response?.content?.getValue("image/png")?.schema?.format)
        assertEquals("binary", response?.content?.getValue("image/jpeg")?.schema?.format)
    }

    @MultiEngineTest
    fun testSerializedResponseWithContentType(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::serializedResponse)

        val (_, op) = Extensions.getOperation(engine, api)

        assertEquals(1, op.responses?.getValue("200")?.content?.size)
        assertNotNull(op.responses?.getValue("200")?.content?.getValue("application/json")?.schema)
    }

    @MultiEngineTest
    fun testJsonContentTypeWithoutSchema(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@DocumentationTests::jsonWithoutSchema)

        val (_, op) = Extensions.getOperation(engine, api)

        assertNull(op.responses?.getValue("400")?.content?.getValue("application/problem+json")?.schema)
    }

    @MultiEngineTest
    fun testAsyncResultIsUnwrapped(engine: ServerEngine) = test {
        val api = Inline.create()
            .get {
                yield()
                Result(UnwrappedModel(42))
            }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val schemas = doc.components?.schemas?.keys?.toList() ?: emptyList()

        assertTrue(schemas.contains("UnwrappedModel"))
        assertFalse(schemas.any { it.startsWith("Result") })
    }

    // endregion

    // region Services

    @MultiEngineTest
    fun testServiceIsTagged(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val tag = doc.tags?.first { it.name == "Users" }

        assertEquals("Manages users\n\nAll of them", tag?.description)

        val operation = doc.paths.getValue("/users/{id}").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals(true, operation?.tags?.any { it == "Users" })
    }

    @MultiEngineTest
    fun testMethodTag(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val operation = doc.paths.getValue("/users/admin").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals(true, operation?.tags?.any { it == "Administration" })
    }

    @MultiEngineTest
    fun testSharedResponses(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val get = doc.paths.getValue("/users/{id}").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals("Something went wrong", get?.responses?.getValue("400")?.description)
        assertEquals("There is no such user", get?.responses?.getValue("404")?.description)

        val admin = doc.paths.getValue("/users/admin").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals("Something went wrong", admin?.responses?.getValue("400")?.description)
        assertEquals("Not Found", admin?.responses?.getValue("404")?.description)
    }

    @MultiEngineTest
    fun testServiceOperationIsDescribed(engine: ServerEngine) = test {
        val doc = getServiceDocument(engine)

        val get = doc.paths.getValue("/users/{id}").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals("Reads a user", get?.summary)
        assertEquals("The ID of the user", get?.parameters?.first()?.description)
        assertEquals("The user with the given ID", get?.responses?.getValue("200")?.description)
    }

    @MultiEngineTest
    fun testUndocumentedServiceKeepsClassName(engine: ServerEngine) = test {
        val api = Layout.create()
            .addService<UndocumentedService>("plain")
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val tag = doc.tags?.first()

        assertEquals(UndocumentedService::class.simpleName, tag?.name)
        assertNull(tag?.description)
    }

    private suspend fun getServiceDocument(engine: ServerEngine): OpenAPI {
        val api = Layout.create()
            .addService<DocumentedService>("users")
            .add(ApiDescription.create())

        return api.getOpenApi(engine).document!!
    }

    // endregion

    // region Schemas

    @MultiEngineTest
    fun testModelIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { DocumentedModel("Test", 42) }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val schema = doc.components?.schemas?.getValue(DocumentedModel::class.simpleName!!)

        assertEquals("A user\n\nWith details", schema?.description)

        assertEquals("The name of the user", getProperty(schema, "Name")?.description)
        assertEquals("The age of the user", getProperty(schema, "Age")?.description)
    }

    @MultiEngineTest
    fun testClassModelIsDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { ClassModel() }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val schema = doc.components?.schemas?.getValue(ClassModel::class.simpleName!!)

        assertEquals("The value", getProperty(schema, "Value")?.description)
        assertNull(getProperty(schema, "Other")?.description)
    }

    private fun getProperty(schema: Schema<*>?, name: String): Schema<*>? =
        schema?.properties?.entries?.first { it.key.equals(name, ignoreCase = true) }?.value

    // endregion

    // region Handlers (lambdas cannot be annotated, so they are declared as functions)

    @Summary("Returns the answer")
    @Remarks("To everything")
    fun answer(): Int = 42

    fun describedParameters(@Summary("The ID of the user") id: Int, @Summary("The fields to return") fields: String?): Int = id

    fun describedResult(): @Summary("The answer") Int = 42

    fun describedDynamicResult(request: Request): @Summary("Whatever happens") Response = request.respond().build()

    fun describedContent(@Summary("The user to create") user: DocumentedModel): DocumentedModel = user

    fun describedBody(@FromBody @Summary("The text") text: String): String = text

    @ApiRequestBody("The content of the file", contentType = "application/octet-stream")
    fun manuallyReadBody(request: Request): Int = 42

    @ApiRequestBody(contentType = "application/zip")
    fun streamedBody(@Summary("The archive") body: InputStream): Int = 42

    @ApiRequestBody("The picture", contentType = "image/png")
    @ApiRequestBody(contentType = "image/jpeg")
    fun multipleBodies(body: InputStream): Int = 42

    @ApiRequestBody("The user", type = DocumentedModel::class)
    fun declaredBodyType(request: Request): Int = 42

    @ApiRequestBody("The user to create")
    fun declaredDescription(user: DocumentedModel): DocumentedModel = user

    @ApiRequestBody("Some text", contentType = "text/plain; charset=utf-8")
    fun declaredTextBody(request: Request): Int = 42

    @ApiRequestBody("Nothing", contentType = "text/plain")
    fun declaredBodyIgnoredForGet(request: Request): Int = 42

    @ApiRequestBody("Nothing to see")
    fun declaredBodyWithoutContent(request: Request): Int = 42

    @ApiResponse(ResponseStatus.Created, "The newly created user")
    fun declaredSuccess(user: DocumentedModel): Result<DocumentedModel> = Result(user).status(ResponseStatus.Created)

    @ApiResponse(ResponseStatus.Accepted)
    fun declaredSuccessWithSummary(user: DocumentedModel): @Summary("The accepted user") DocumentedModel = user

    @ApiResponse(ResponseStatus.NotFound, "There is no such user", type = ErrorModel::class)
    fun errorResponse(id: Int): DocumentedModel = DocumentedModel("Test", 42)

    @ApiResponse(ResponseStatus.TooManyRequests)
    fun errorResponseWithoutContent(): Int = 42

    @ApiResponse(ResponseStatus.NoContent)
    fun noContent() {
    }

    @ApiResponse(ResponseStatus.Conflict, "The number of conflicts", type = Int::class)
    fun formattableResponse(): Int = 42

    @ApiResponse(ResponseStatus.Ok, "The picture", contentType = "image/png")
    @ApiResponse(ResponseStatus.Ok, contentType = "image/jpeg")
    fun dynamicResponse(request: Request): Response = request.respond().build()

    @ApiResponse(ResponseStatus.Ok, "The user", contentType = "application/json")
    fun serializedResponse(): DocumentedModel = DocumentedModel("Test", 42)

    @ApiResponse(ResponseStatus.BadRequest, "The problem", contentType = "application/problem+json")
    fun jsonWithoutSchema(): Int = 42

    // endregion

    // region Supporting data structures

    @Summary("A user")
    @Remarks("With details")
    data class DocumentedModel(@Summary("The name of the user") val name: String, @property:Summary("The age of the user") val age: Int)

    data class ErrorModel(val message: String)

    data class UnwrappedModel(val value: Int)

    class ClassModel {

        @Summary("The value")
        var value: Int = 0

        var other: Int = 0

    }

    @Tag("Users")
    @Summary("Manages users")
    @Remarks("All of them")
    @ApiResponse(ResponseStatus.BadRequest, "Something went wrong", type = ErrorModel::class)
    @ApiResponse(ResponseStatus.NotFound)
    class DocumentedService {

        @ResourceMethod(path = ":id")
        @Summary("Reads a user")
        @ApiResponse(ResponseStatus.NotFound, "There is no such user")
        fun get(@Summary("The ID of the user") id: Int): @Summary("The user with the given ID") DocumentedModel = DocumentedModel("Test", id)

        @ResourceMethod(path = "admin")
        @Tag("Administration")
        fun getAdmin(): Int = 42

    }

    class UndocumentedService {

        @ResourceMethod
        fun get(): Int = 42

    }

    // endregion

}
