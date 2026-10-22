package org.genhttp.testing.acceptance.modules.inspection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.inspection.Inspector
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader

import kotlin.test.assertEquals

class SerializationTests {

    @MultiEngineTest
    fun testCustomFormat(engine: ServerEngine) = test {
        val serialization = Serialization.empty()
            .default(ContentType.ApplicationJson)
            .add(ContentType.ApplicationJson, JsonFormat())
            .build()

        val inspection = Inspector.create().serialization(serialization)

        val app = Layout.create().add(inspection)

        TestHost.run(app, engine = engine).use { host ->
            val inspected = host.getResponse("/one/two?inspect")

            inspected.assertStatus(ResponseStatus.Ok)

            assertEquals("application/json", inspected.getContentHeader("Content-Type"))
        }
    }

    @MultiEngineTest
    fun testNoFormats(engine: ServerEngine) = test {
        val serialization = Serialization.empty()
            .default(ContentType.AudioMp4)
            .build()

        val inspection = Inspector.create().serialization(serialization)

        val app = Layout.create().add(inspection)

        TestHost.run(app, engine = engine).use { host ->
            val inspected = host.getResponse("/one/two?inspect")

            inspected.assertStatus(ResponseStatus.UnsupportedMediaType)
        }
    }

}
