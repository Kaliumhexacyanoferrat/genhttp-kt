package org.genhttp.testing.acceptance.modules.conversion

import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import tools.jackson.databind.SerializationFeature

class SerializerTests {

    // region Supporting data structures

    data class MyType(val stringValue: String, val intValue: Int)

    // endregion

    // region Tests

    @Test
    fun testCustomJsonOptions() = test {
        val options = JsonFormat.getDefaultOptions()
            .disable(SerializationFeature.INDENT_OUTPUT)

        val serialization = Serialization.default(jsonMapper = options.build())

        val api = Inline.create()
            .get { MyType("123", 456) }
            .serializers(serialization)

        TestHost.run(api).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            AssertX.doesNotContain("\n", response.getContent())
        }
    }

    // endregion

}
