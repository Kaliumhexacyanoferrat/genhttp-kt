package org.genhttp.testing.acceptance.modules.openapi

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

import org.genhttp.api.content.services.Summary
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.functional.*
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SchemaTests {

    // region Supporting data structures

    data class Key(val publicKey: String, val keyLength: Int)

    class FileModel {

        var name: String = "file.txt"

        @JsonIgnore
        var isCompiled: Boolean = false

        @JsonProperty("size_in_bytes")
        @Summary("The size of the file")
        var size: Long = 42

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testPropertiesFollowDefaultNamingPolicy(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { Key("abc", 2048) }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val properties = doc.components?.schemas?.get(Key::class.simpleName)?.properties!!

        assertEquals(setOf("publicKey", "keyLength"), properties.keys)
    }

    @MultiEngineTest
    fun testSchemaMatchesWireFormat(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { FileModel() }
            .add(ApiDescription.create())

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            val json = JsonMapper().readTree(response.getContent())

            val sent = json.propertyNames().toSet()

            val specResponse = host.getResponse("/openapi.json")

            val doc = specResponse.asOpenApi().document!!

            val described = doc.components?.schemas?.get(FileModel::class.simpleName)?.properties!!.keys

            assertEquals(sent, described)
        }
    }

    @MultiEngineTest
    fun testIgnoredPropertiesAreNotDescribed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { FileModel() }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val properties = doc.components?.schemas?.get(FileModel::class.simpleName)?.properties!!

        assertFalse(properties.keys.any { it.equals(FileModel::isCompiled.name, ignoreCase = true) })
    }

    @MultiEngineTest
    fun testRenamedPropertyKeepsDescription(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { FileModel() }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val properties = doc.components?.schemas?.get(FileModel::class.simpleName)?.properties!!

        assertEquals("The size of the file", properties.getValue("size_in_bytes").description)
    }

    @MultiEngineTest
    fun testCustomSerializerOptionsAreRespected(engine: ServerEngine) = test {
        val options = JsonFormat.getDefaultOptions()

        options.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

        val api = Inline.create()
            .serializers(Serialization.default(options.build()))
            .get { Key("abc", 2048) }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val properties = doc.components?.schemas?.get(Key::class.simpleName)?.properties!!

        assertEquals(setOf("public_key", "key_length"), properties.keys)
    }

    @MultiEngineTest
    fun testRequestBodyFollowsNamingPolicy(engine: ServerEngine) = test {
        val api = Inline.create()
            .post { key: Key -> key.keyLength }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document!!

        val properties = doc.components?.schemas?.get(Key::class.simpleName)?.properties!!

        assertEquals(setOf("publicKey", "keyLength"), properties.keys)
    }

    // endregion

}
