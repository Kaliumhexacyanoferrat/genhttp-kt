package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.infrastructure.tryGet
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import org.slf4j.ILoggerFactory

import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestPropertyTest {

    @MultiEngineTest
    fun testRequestProperties(engine: ServerEngine) = test {
        val app = Inline.create().get { request: Request ->
            request.properties["my"] = "value"

            assertTrue(request.properties.tryGet<String>("my") != null)

            request.properties.clear("my")

            assertFalse(request.properties.tryGet<String>("my") != null)

            assertFailsWith<NoSuchElementException> { request.properties["my"] }

            true
        }

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testGetBodyTwiceThrows(engine: ServerEngine) = test {
        val app = Inline.create().get { request: Request ->
            request.getBody()

            assertFailsWith<IllegalStateException> { request.getBody() }

            true
        }

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testHeaderAccessibleAfterBodyLoaded(engine: ServerEngine) = test {
        val app = Inline.create().get { request: Request ->
            request.getBody()

            request.header.target.asString(decode = false)
        }

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @Test
    fun testApplyWithoutEndpoint() {
        val request = HttpRequest()

        val server = StubServer()

        request.apply(server)

        assertSame(server, request.server)
        assertNotNull(request.header)
        assertNotNull(request.properties)
    }

    // region Supporting types

    /**
     * Stands in for the mocked server of the upstream test (nothing but identity is used).
     */
    private class StubServer : Server {

        override val serverEngine: ServerEngine
            get() = ServerEngine.Internal

        override val version: String
            get() = "stub"

        override val running: Boolean
            get() = true

        override val development: Boolean
            get() = false

        override val handler: Handler
            get() = throw UnsupportedOperationException()

        override val properties: PropertyBag
            get() = throw UnsupportedOperationException()

        override val logging: ILoggerFactory
            get() = throw UnsupportedOperationException()

        override val endPoints: EndPointCollection
            get() = throw UnsupportedOperationException()

        override suspend fun start() {}

        override suspend fun close() {}

    }

    // endregion

}
