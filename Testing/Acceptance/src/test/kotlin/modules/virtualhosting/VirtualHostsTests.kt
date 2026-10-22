package org.genhttp.testing.acceptance.modules.virtualhosting

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.virtualhosting.VirtualHosts
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class VirtualHostsTests {

    /**
     * As a hoster, I would like to provide several domains using the
     * same server instance.
     */
    @MultiEngineTest
    fun testDomains(engine: ServerEngine) = test {
        val hosts = VirtualHosts.create()
            .add("domain1.com", Content.from(Resource.fromString("domain1.com")))
            .add("domain2.com", Content.from(Resource.fromString("domain2.com")))
            .default(Layout.create().index(Content.from(Resource.fromString("default"))))

        TestHost.run(hosts, engine = engine).use { runner ->
            runTest(runner, "domain1.com")
            runTest(runner, "domain2.com")

            runTest(runner, "localhost", "default")
        }
    }

    /**
     * As a developer, I expect the server to return no content if
     * no given route matches.
     */
    @MultiEngineTest
    fun testNoDefault(engine: ServerEngine) = test {
        TestHost.run(VirtualHosts.create(), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testConcernChaining() {
        Chain.works(VirtualHosts.create())
    }

    private suspend fun runTest(runner: TestHost, host: String, expected: String? = null) {
        val request = runner.getRequest()
        request.header("Host", host)

        val response = runner.getResponse(request)

        assertEquals(expected ?: host, response.getContent())
    }

}
