package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

class MethodTests {

    @MultiEngineTest
    fun testCustomMethods(engine: ServerEngine) = test {
        val result = Inline.create().on(setOf(RequestMethod("BREW"))) { "Hmm, Beer" }

        TestHost.run(result, engine = engine).use { host ->
            val request = host.getRequest(method = "BREW")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

}
