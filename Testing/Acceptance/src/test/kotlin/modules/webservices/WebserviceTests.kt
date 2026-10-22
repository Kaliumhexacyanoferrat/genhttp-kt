package org.genhttp.testing.acceptance.modules.webservices

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.Method
import org.genhttp.modules.reflection.Result
import org.genhttp.modules.conversion.serializers.xml.XmlFormat
import kotlin.reflect.typeOf
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.ServiceResource
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import tools.jackson.databind.DeserializationFeature
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.UUID

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WebserviceTests {

    // region Supporting structures

    class TestEntity {

        var id: Int = 0

        var nullable: Double? = null

    }

    enum class TestEnum {
        One,
        Two
    }

    class TestResource {

        @ResourceMethod(path = "nothing")
        fun doNothing() {
        }

        @ResourceMethod(path = "primitive")
        fun primitive(input: Int): Int = input

        @ResourceMethod(path = "guid")
        fun guid(id: UUID): UUID = id

        @ResourceMethod(Method.Post, "entity")
        fun entity(entity: TestEntity): TestEntity = entity

        @ResourceMethod(Method.Get, "entity-result")
        suspend fun entityResult(): Result<TestEntity> =
            Result(TestEntity().apply { id = 12 }).status(ResponseStatus.Created)

        @ResourceMethod(Method.Put, "stream")
        fun stream(input: InputStream): InputStream = ByteArrayInputStream(input.readAllBytes().size.toString().toByteArray(Charsets.UTF_8))

        @ResourceMethod(path = "bytes")
        fun bytes(): ByteArray = "Hello Bytes".toByteArray(Charsets.UTF_8)

        @ResourceMethod(path = "memory")
        fun memory(): ByteBuffer = ByteBuffer.wrap("Hello Memory".toByteArray(Charsets.UTF_8))

        @ResourceMethod(path = "requestResponse")
        suspend fun requestResponse(request: Request): Response? = request.respond()
            .content(StringContent("Hello World"))
            .build()

        @ResourceMethod(path = "exception")
        fun exception() {
            throw ProviderException(ResponseStatus.AlreadyReported, "Already reported!")
        }

        @ResourceMethod(path = "duplicate")
        fun duplicate1() {
        }

        @ResourceMethod(path = "duplicate")
        fun duplicate2() {
        }

        @ResourceMethod(path = "param/:param")
        fun pathParam(param: Int): Int = param

        @ResourceMethod(path = "regex/(?<param>[0-9]+)")
        fun regexParam(param: Int): Int = param

        @ResourceMethod
        fun empty() {
        }

        @ResourceMethod(path = "enum")
        fun enum(input: TestEnum): TestEnum = input

        @ResourceMethod(path = "nullable")
        fun nullable(input: Int?): Int? = input

        @ResourceMethod(path = "request")
        fun request(handler: Handler, request: Request): String = "yes"

    }

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testEmpty(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "") { r -> r.assertStatus(ResponseStatus.NoContent) }
    }

    @MultiEngineFrameworkTest
    fun testVoidReturn(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "nothing") { r -> r.assertStatus(ResponseStatus.NoContent) }
    }

    @MultiEngineFrameworkTest
    fun testPrimitives(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "primitive?input=42") { r -> assertEquals("42", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testEnums(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "enum?input=One") { r -> assertEquals("One", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testNullableSet(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "nullable?input=1") { r -> assertEquals("1", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testNullableNotSet(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "nullable") { r -> r.assertStatus(ResponseStatus.NoContent) }
    }

    @MultiEngineFrameworkTest
    fun testGuid(engine: ServerEngine, mode: ExecutionMode) = test {
        val id = UUID.randomUUID().toString()

        withResponse(engine, mode, "guid?id=$id") { r -> assertEquals(id, r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testParam(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "param/42") { r -> assertEquals("42", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testConversionFailure(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "param/abc") { r -> r.assertStatus(ResponseStatus.BadRequest) }
    }

    @MultiEngineFrameworkTest
    fun testRegex(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "regex/42") { r -> assertEquals("42", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testEntityWithNulls(engine: ServerEngine, mode: ExecutionMode) = test {
        val entity = "{\"id\":42}"
        withResponse(engine, mode, "entity", "POST", entity, null, null) { r -> assertEquals(entity, r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testEntityWithNoNulls(engine: ServerEngine, mode: ExecutionMode) = test {
        val entity = "{\"id\":42,\"nullable\":123.456}"
        withResponse(engine, mode, "entity", "POST", entity, null, null) { r -> assertEquals(entity, r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testNotSupportedUpload(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "entity", "POST", "123", "bla/blubb", null) { r -> r.assertStatus(ResponseStatus.UnsupportedMediaType) }
    }

    @MultiEngineFrameworkTest
    fun testUnsupportedDownloadEnforcesDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        val entity = "{\"id\":42,\"nullable\":123.456}"
        withResponse(engine, mode, "entity", "POST", entity, null, "bla/blubb") { r -> assertEquals(entity, r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testEntityWithResult(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "entity-result", "GET", null, null, null) { r -> r.assertStatus(ResponseStatus.Created) }
    }

    @MultiEngineFrameworkTest
    fun testNoMethod(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "idonotexist") { r -> r.assertStatus(ResponseStatus.NotFound) }
    }

    @MultiEngineFrameworkTest
    fun testStream(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "stream", "PUT", "123456", null, null) { r -> assertEquals("6", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testByteArrayReturn(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "bytes") { r ->
            r.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello Bytes", r.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testReadOnlyMemoryReturn(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "memory") { r ->
            r.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello Memory", r.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testRequestResponse(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "requestResponse") { r -> assertEquals("Hello World", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testRouting(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "request") { r -> assertEquals("yes", r.getContent()) }
    }

    @MultiEngineFrameworkTest
    fun testEntityAsXml(engine: ServerEngine, mode: ExecutionMode) = test {
        val entity = "<TestEntity><Id>1</Id><Nullable>1234.56</Nullable></TestEntity>"

        for (requestType in listOf("text/xml", "application/xml")) {
            withResponse(engine, mode, "entity", "POST", entity, requestType, "application/xml") { r ->
                // upstream deserializes with XmlSerializer, the serializer used by the server as well
                val result = XmlFormat().deserialize(java.io.ByteArrayInputStream(r.body()), typeOf<TestEntity>()) as TestEntity

                assertNotNull(result)

                assertEquals(1, result.id)
                assertEquals(1234.56, result.nullable)
            }
        }
    }

    @MultiEngineFrameworkTest
    fun testEntityAsYaml(engine: ServerEngine, mode: ExecutionMode) = test {
        val entity = """
            id: 1
            nullable: 1234.56
            """.trimIndent()

        withResponse(engine, mode, "entity", "POST", entity, "application/yaml", "application/yaml") { r ->
            r.assertStatus(ResponseStatus.Ok)

            val mapper = YAMLMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()

            val result = mapper.readValue(r.body(), TestEntity::class.java)

            assertNotNull(result)

            assertEquals(1, result.id)
            assertEquals(1234.56, result.nullable)
        }
    }

    @MultiEngineFrameworkTest
    fun testException(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "exception") { r -> r.assertStatus(ResponseStatus.AlreadyReported) }
    }

    @MultiEngineFrameworkTest
    fun testDuplicate(engine: ServerEngine, mode: ExecutionMode) = test {
        withResponse(engine, mode, "duplicate") { r -> r.assertStatus(ResponseStatus.BadRequest) }
    }

    @MultiEngineFrameworkTest
    fun testWithInstance(engine: ServerEngine, mode: ExecutionMode) = test {
        val layout = Layout.create().addService("t", TestResource(), mode = mode)

        TestHost.run(layout).use { runner ->
            val response = runner.getResponse("/t")

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @Test
    fun testConcernChaining() {
        val service = ServiceResource.from<TestResource>()

        Chain.works(service)
    }

    // endregion

    // region Helpers

    private suspend fun withResponse(
        engine: ServerEngine,
        mode: ExecutionMode,
        uri: String,
        logic: suspend (HttpResponse<ByteArray>) -> Unit
    ) = withResponse(engine, mode, uri, "GET", null, null, null, logic)

    private suspend fun withResponse(
        engine: ServerEngine,
        mode: ExecutionMode,
        uri: String,
        method: String,
        body: String?,
        contentType: String?,
        accept: String?,
        logic: suspend (HttpResponse<ByteArray>) -> Unit
    ) {
        getService(engine, mode).use { service ->
            val request = service.getRequest("/t/$uri", method)

            if (accept != null) {
                request.header("Accept", accept)
            }

            if (body != null) {
                request.method(method, BodyPublishers.ofString(body))

                if (contentType != null) {
                    request.header("Content-Type", contentType)
                }
            }

            val response = service.getResponse(request)

            logic(response)
        }
    }

    private suspend fun getService(engine: ServerEngine, mode: ExecutionMode): TestHost {
        val service = ServiceResource.from<TestResource>()
            .serializers(Serialization.default())
            .injectors(Injection.default())
            .executionMode(mode)

        return TestHost.run(Layout.create().add("t", service), engine = engine)
    }

    // endregion

}
