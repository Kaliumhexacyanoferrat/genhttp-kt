package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.dependencyinjection.Dependent
import org.genhttp.modules.dependencyinjection.DependentConcern
import org.genhttp.modules.dependencyinjection.DependentHandler
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import kotlin.test.assertEquals

class BasicTests {

    // region Supporting data structures

    class MyConcern(private val service: AwesomeService) : DependentConcern {

        override suspend fun handle(content: Handler, request: Request): Response? {
            val response = content.handle(request)

            response?.rebuild()?.header("X-Custom", service.doWork())

            return response
        }

    }

    class MyHandler(private val service: AwesomeService) : DependentHandler {

        override suspend fun handle(request: Request): Response? =
            request.respond()
                .content(StringContent(service.doWork()))
                .build()

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testDependentConcern(engine: ServerEngine) = test {
        val concern = Dependent.concern<MyConcern>()

        val app = Inline.create()
            .get { "Yay" }
            .add(concern)

        DependentHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("42", response.getHeader("X-Custom"))
        }
    }

    @MultiEngineTest
    fun testDependentHandler(engine: ServerEngine) = test {
        val handler = Dependent.handler<MyHandler>()

        DependentHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("42", response.getContent())
        }
    }

    // endregion

}
