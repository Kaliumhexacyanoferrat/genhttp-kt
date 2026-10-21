package org.genhttp.testing.acceptance.modules.conversion

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

import org.genhttp.modules.conversion.serializers.SerializationFormat
import org.genhttp.modules.conversion.serializers.forms.FormFormat
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.conversion.serializers.xml.XmlFormat
import org.genhttp.modules.conversion.serializers.yaml.YamlFormat
import org.genhttp.modules.protobuf.providers.ProtobufFormat
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream

import kotlin.reflect.typeOf
import kotlin.test.assertEquals

class CycleTests {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable
    class TestType {

        @ProtoNumber(1)
        var message: String? = null

    }

    @Test
    fun testFormats() = test {
        val obj = TestType().apply {
            message = "There and back"
        }

        val subjects = listOf<SerializationFormat>(
            JsonFormat(), FormFormat(), YamlFormat(), XmlFormat(), ProtobufFormat()
        )

        for (subject in subjects) {
            val serialized = subject.serialize(obj)

            val bytes = ByteArray(serialized.remaining())
            serialized.duplicate().get(bytes)

            ByteArrayInputStream(bytes).use { stream ->
                val clone = subject.deserialize(stream, typeOf<TestType>())

                assertEquals(obj.message, (clone as TestType).message)
            }
        }
    }

}
