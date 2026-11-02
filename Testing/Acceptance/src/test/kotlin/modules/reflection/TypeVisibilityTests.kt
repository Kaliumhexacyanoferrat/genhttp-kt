package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.ServiceResource
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class TypeVisibilityTests {

    private class MyPrivateService {

        @ResourceMethod
        internal fun sum(a: Int, b: Int): Int = a + b

    }

    @MultiEngineFrameworkTest
    fun testPrivateMembersCanBeAccessed(engine: ServerEngine, mode: ExecutionMode) = test {
        val api = ServiceResource.from<MyPrivateService>()
            .executionMode(mode)

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse("/?a=1&b=5")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("6", response.getContent())
        }
    }

}
