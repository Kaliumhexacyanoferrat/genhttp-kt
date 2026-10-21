package org.genhttp.testing.acceptance.modules.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.protobuf.addProtobuf
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.Method
import org.genhttp.modules.reflection.ResourceMethod
import org.genhttp.modules.webservices.ServiceResource
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class ProtobufTests {

    // region Supporting structures

    @Serializable
    class TestEntity {

        @ProtoNumber(1)
        var id: Int = 0

        @ProtoNumber(2)
        var name: String? = null

        @ProtoNumber(3)
        var nullable: Double? = null

    }

    class TestResource {

        @ResourceMethod
        fun getEntity(): TestEntity {
            val entity = TestEntity().apply {
                id = 1
                name = "test1"
            }

            return entity
        }

        @ResourceMethod(Method.Post)
        fun postEntity(entity: TestEntity): TestEntity = entity

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testGetEntityAsProtobuf(engine: ServerEngine) = test {
        var result: TestEntity? = null

        withResponse("", "GET", null, "application/protobuf", "application/protobuf", { r ->
            result = ProtoBuf.decodeFromByteArray<TestEntity>(r.body())
        }, engine)

        assertEquals(1, result!!.id)
        assertEquals("test1", result!!.name)
    }

    @MultiEngineTest
    fun testPostEntityAsProtobuf(engine: ServerEngine) = test {
        val entity = TestEntity().apply {
            id = 2
            name = "test2"
            nullable = null
        }

        val encodedEntity = ProtoBuf.encodeToByteArray(entity)

        var result: TestEntity? = null

        withResponse("", "POST", encodedEntity, "application/protobuf", "application/protobuf", { r ->
            result = ProtoBuf.decodeFromByteArray<TestEntity>(r.body())
        }, engine)

        assertNotNull(result)
        assertEquals(entity.id, result!!.id)
        assertEquals(entity.name, result!!.name)
        assertNull(result!!.nullable)
    }

    // endregion

    // region Helpers

    private suspend fun withResponse(
        uri: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        accept: String?,
        logic: suspend (HttpResponse<ByteArray>) -> Unit,
        engine: ServerEngine
    ) {
        getService(engine).use { service ->
            val request = service.getRequest("/t/$uri", method)

            if (accept != null) {
                request.header("Accept", accept)
            }

            if (body != null) {
                request.method(method, BodyPublishers.ofByteArray(body))

                if (contentType != null) {
                    request.header("Content-Type", contentType)
                }
            }

            val response = service.getResponse(request)

            logic(response)
        }
    }

    private suspend fun getService(engine: ServerEngine): TestHost {
        val service = ServiceResource.from<TestResource>()
            .serializers(Serialization.default().addProtobuf())
            .injectors(Injection.default())

        return TestHost.run(Layout.create().add("t", service), engine = engine)
    }

    // endregion

}
