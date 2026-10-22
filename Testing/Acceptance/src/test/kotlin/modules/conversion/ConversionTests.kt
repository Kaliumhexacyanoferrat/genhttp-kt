package org.genhttp.testing.acceptance.modules.conversion

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.serializers.SerializationFormat
import org.genhttp.modules.conversion.serializers.forms.FormFormat
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.http.HttpRequest.BodyPublishers

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.createType
import kotlin.test.assertEquals

class ConversionTests {

    // region Helpers

    private suspend inline fun <reified TFormat : SerializationFormat, reified TData : Any> runTest(serialized: String, engine: ServerEngine) =
        runTest(TFormat::class, TData::class, serialized, engine)

    private suspend fun runTest(format: KClass<out SerializationFormat>, data: KClass<*>, serialized: String, engine: ServerEngine) {
        val handler = ConversionHandlerBuilder(format.createInstance(), data)

        TestHost.run(handler, engine = engine).use { runner ->
            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofString(serialized))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
            assertEquals(serialized, response.getContent())
        }
    }

    // endregion

    // region Supporting data structures

    class FieldData {

        @JvmField
        var field: Int = 0

    }

    @Suppress("PropertyName")
    class PropertyData {

        var Field: Int = 0

    }

    @Suppress("PropertyName")
    class TypedData {

        var Boolean: Boolean = false

        var Double: Double = 0.0

        var String: String? = null

        var Enum: EnumData = EnumData.One

    }

    enum class EnumData { One, Two }

    class ConversionHandlerBuilder(private val format: SerializationFormat, private val type: KClass<*>) : HandlerBuilder {

        override fun build(): Handler = ConversionHandler(format, type)

    }

    class ConversionHandler(private val format: SerializationFormat, private val type: KClass<*>) : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            val body = request.getBody(HeaderAccess.Retain)

            val obj = format.deserialize(body!!.asStream(), type.createType())

            return format.serialize(request, obj!!).build()
        }

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testFormFieldSerialization(engine: ServerEngine) = test { runTest<FormFormat, FieldData>("field=20", engine) }

    @MultiEngineTest
    fun testFormPropertySerialization(engine: ServerEngine) = test { runTest<FormFormat, PropertyData>("Field=20", engine) }

    @MultiEngineTest
    fun testFormTypeSerialization(engine: ServerEngine) = test { runTest<FormFormat, TypedData>("Boolean=1&Double=0.2&String=Test&Enum=One", engine) }

    @MultiEngineTest
    fun testFormDefaultValueSerialization(engine: ServerEngine) = test { runTest<FormFormat, TypedData>("Boolean=0&Double=0&String=&Enum=One", engine) }

    // endregion

}
