package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

class ErrorHandlingTests {

    @MultiEngineFrameworkTest
    fun testSerializationNotPossible(engine: ServerEngine, mode: ExecutionMode) = test {
        val serialization = Serialization.empty()
            .default(ContentType.AudioMp4)

        val api = Inline.create()
            .get { HashSet<Int>() }
            .serializers(serialization)
            .executionMode(mode)

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.UnsupportedMediaType)
        }
    }

}
