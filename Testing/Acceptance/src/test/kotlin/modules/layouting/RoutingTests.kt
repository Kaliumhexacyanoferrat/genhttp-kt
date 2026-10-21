package org.genhttp.testing.acceptance.modules.layouting

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

class RoutingTests {

    /**
     * As a client, I expect the server to return 404 for non-existing files.
     */
    @MultiEngineTest
    fun notFoundForUnknownRoute(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

}
