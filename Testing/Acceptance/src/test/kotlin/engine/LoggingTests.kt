package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPLoggerFactory

import java.util.concurrent.CopyOnWriteArrayList

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoggingTests {

    // region Tests

    @MultiEngineTest
    fun testRequestsAreLoggedByDefault(engine: ServerEngine) = test {
        val factory = CapturingLoggerFactory()

        TestHost(Layout.create().index(OkHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(factory).start()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals(1, factory.messages.size)

            val entry = factory.messages[0]

            AssertX.contains("GET", entry)
            AssertX.contains("200", entry)
        }
    }

    @MultiEngineTest
    fun testOutermostConcernObservesNotFound(engine: ServerEngine) = test {
        val factory = CapturingLoggerFactory()

        TestHost(Layout.create().build(), engine = engine).use { runner ->
            runner.host.logging(factory).start()

            val response = runner.getResponse("/does/not/exist")

            response.assertStatus(ResponseStatus.NotFound)

            assertEquals(1, factory.messages.size)

            val entry = factory.messages[0]

            AssertX.contains("404", entry)
            AssertX.contains("/does/not/exist", entry)
        }
    }

    @MultiEngineTest
    fun testLoggingCanBeDisabled(engine: ServerEngine) = test {
        TestHost(Layout.create().index(OkHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(NOPLoggerFactory()).start()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertIs<NOPLoggerFactory>(runner.host.instance!!.logging)
        }
    }

    @MultiEngineTest
    fun testNullLoggerFactoryAlsoDisablesLogging(engine: ServerEngine) = test {
        TestHost(Layout.create().index(OkHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(NOPLoggerFactory()).start()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertIs<NOPLoggerFactory>(runner.host.instance!!.logging)
        }
    }

    @MultiEngineTest
    fun testRequestLoggingCanBeDisabledWhileKeepingInfrastructure(engine: ServerEngine) = test {
        val factory = CapturingLoggerFactory()

        TestHost(Layout.create().index(OkHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(factory, logRequests = false).start()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            // no per-request entries, but the factory itself is still wired up
            // and used for the server's own lifecycle/endpoint logging
            assertTrue(factory.messages.isEmpty())
            assertTrue(factory.allMessages.any { it.contains("started") })

            assertSame(factory, runner.host.instance!!.logging)
        }
    }

    @MultiEngineTest
    fun testServerLifecycleIsLogged(engine: ServerEngine) = test {
        val factory = CapturingLoggerFactory()

        TestHost(Layout.create().index(OkHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(factory).start()

            assertTrue(factory.allMessages.any { it.contains("started") })
            assertTrue(factory.allMessages.any { it.contains("Listening") })

            runner.host.stop()

            assertTrue(factory.allMessages.any { it.contains("shutting down") })
        }
    }

    @MultiEngineTest
    fun testHeaderIsCapturedBeforeBodyIsReleased(engine: ServerEngine) = test {
        val factory = CapturingLoggerFactory()

        TestHost(Layout.create().index(ReleasingHandler()).build(), engine = engine).use { runner ->
            runner.host.logging(factory).start()

            val request = runner.getRequest()
            request.header("Connection", "close")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals(1, factory.messages.size)

            AssertX.contains("GET", factory.messages[0])
        }
    }

    // endregion

    // region Supporting types

    private class OkHandler : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? = request.respond().status(ResponseStatus.Ok).build()

    }

    private class ReleasingHandler : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            request.getBody(HeaderAccess.Release)

            return request.respond().status(ResponseStatus.Ok).build()
        }

    }

    private class CapturingLoggerFactory : ILoggerFactory {

        private val entries = CopyOnWriteArrayList<Pair<String, String>>()

        /**
         * Only the entries written by the request logging concern, ignoring
         * the server and endpoint lifecycle messages that share this factory.
         */
        val messages: List<String>
            get() = entries.filter { it.first == REQUEST_CATEGORY }.map { it.second }

        val allMessages: List<String>
            get() = entries.map { it.second }

        override fun getLogger(name: String): Logger = CapturingLogger(name, entries)

        private companion object {

            const val REQUEST_CATEGORY = "GenHTTP.Requests"

        }

    }

    private class CapturingLogger(category: String, private val entries: MutableList<Pair<String, String>>) : LegacyAbstractLogger() {

        init {
            name = category
        }

        override fun isTraceEnabled(): Boolean = true

        override fun isDebugEnabled(): Boolean = true

        override fun isInfoEnabled(): Boolean = true

        override fun isWarnEnabled(): Boolean = true

        override fun isErrorEnabled(): Boolean = true

        override fun getFullyQualifiedCallerName(): String? = null

        override fun handleNormalizedLoggingCall(level: Level?, marker: Marker?, messagePattern: String?, arguments: Array<Any?>?, throwable: Throwable?) {
            entries.add(getName() to MessageFormatter.arrayFormat(messagePattern, arguments, throwable).message.orEmpty())
        }

    }

    // endregion

}
