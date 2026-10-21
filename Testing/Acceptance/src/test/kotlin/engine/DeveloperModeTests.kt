package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

class DeveloperModeTests {

    /**
     * As a developer of a web project, I would like to see exceptions rendered
     * in the browser, so that I can trace an error more quickly
     */
    @MultiEngineTest
    fun testExceptionsWithTrace(engine: ServerEngine) = test {
        TestHost(Layout.create().build(), engine = engine).use { runner ->
            val router = Layout.create().index(ThrowingProvider().wrap())

            runner.host.handler(router).development().start()

            val response = runner.getResponse()

            // JVM stack traces are rendered as "at org.genhttp...", .NET ones as "at GenHTTP..."
            AssertX.contains("at org.genhttp", response.getContent())
        }
    }

    /**
     * As a devops member, I do not want an web application to leak internal
     * implementation detail with exception messages
     */
    @MultiEngineTest
    fun testExceptionsWithNoTrace(engine: ServerEngine) = test {
        val router = Layout.create().index(ThrowingProvider().wrap())

        TestHost.run(router, development = false, engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.doesNotContain("at org.genhttp", response.getContent())
        }
    }

    private class ThrowingProvider : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? = throw IllegalStateException("Nope!")

    }

}
