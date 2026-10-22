package org.genhttp.testing.acceptance.modules.serversentevents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.serversentevents.EventConnection
import org.genhttp.modules.serversentevents.EventSource
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import java.net.Socket

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class ProtocolTests {

    private val nl = "\n"

    // region Tests

    @MultiEngineTest
    fun testComment(engine: ServerEngine) = test { testAsync(engine, { c -> c.comment("invisible") }, ": invisible") }

    @MultiEngineTest
    fun testRetry(engine: ServerEngine) = test { testAsync(engine, { c -> c.retry(10000) }, "retry: 10000") }

    @MultiEngineTest
    fun testRetryTwice(engine: ServerEngine) = test {
        testAsync(engine, { c ->
            c.retry(10000)
            c.retry(1)
        }, "retry: 10000")
    }

    @MultiEngineTest
    fun testType(engine: ServerEngine) = test { testAsync(engine, { c -> c.data("data", eventType = "TYPE") }, "event: TYPE${nl}data: data") }

    @MultiEngineTest
    fun testId(engine: ServerEngine) = test { testAsync(engine, { c -> c.data("data", eventId = "4711") }, "id: 4711${nl}data: data") }

    @MultiEngineTest
    fun testNull(engine: ServerEngine) = test { testAsync(engine, { c -> c.data(null as Int?) }, "data: ") }

    @MultiEngineTest
    fun testResume(engine: ServerEngine) = test {
        val source = EventSource.create()
            .inspector { _, id ->
                assertEquals("4711", id)
                true
            }
            .generator { c ->
                assertEquals("4711", c.lastEventId)
            }

        TestHost.run(source, engine = engine).use { host ->
            val request = host.getRequest()

            request.header("Last-Event-ID", "4711")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testNoContent(engine: ServerEngine) = test {
        val source = EventSource.create()
            .inspector { _, _ -> false }
            .generator { _ ->
                fail("Must not happen")
            }

        TestHost.run(source, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineTest
    fun testException(engine: ServerEngine) = test { testAsync(engine, { _ -> throw IllegalStateException("Nope") }, "retry: 30000") }

    @MultiEngineTest
    fun testGetOnly(engine: ServerEngine) = test {
        val source = EventSource.create()
            .generator { }

        TestHost.run(source, engine = engine).use { host ->
            val request = host.getRequest(method = "HEAD")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.MethodNotAllowed)

            assertEquals("GET", response.getContentHeader("Allow"))
        }
    }

    @MultiEngineTest
    fun testRequestAccess(engine: ServerEngine) = test {
        val source = EventSource.create()
            .generator { c ->
                assertNotNull(c.request)
            }

        TestHost.run(source, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    /**
     * As a client closes the connection, the generator learns that it is gone from the next
     * event it sends, so a source that runs until nobody listens comes to an end.
     */
    @MultiEngineTest
    fun testGeneratorNoticesClosedConnection(engine: ServerEngine) = test {
        val left = CompletableDeferred<Unit>()

        val source = EventSource.create()
            .generator { c ->
                while (c.comment("ping")) {
                    delay(50)
                }

                assertFalse(c.connected)
                left.complete(Unit)
            }

        TestHost.run(source, engine = engine).use { host ->
            Socket("127.0.0.1", host.port).use { client ->
                val stream = client.getOutputStream()

                stream.write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.US_ASCII))
                stream.flush()

                // wait for the first event, so the generator is running when the client leaves
                val input = client.getInputStream()

                val buffer = ByteArray(1024)
                var received = ""

                while (!received.contains(": ping")) {
                    val read = input.read(buffer)

                    // the end of the stream is signalled with -1 by the JVM (0 in .NET)
                    assertNotEquals(-1, read)

                    received += String(buffer, 0, read, Charsets.US_ASCII)
                }
            }

            val finished = withTimeoutOrNull(10_000) { left.await() }

            assertNotNull(finished, "The generator kept sending after the client closed the connection")
        }
    }

    private suspend fun testAsync(engine: ServerEngine, generator: suspend (EventConnection) -> Unit, expected: String) {
        val source = EventSource.create()
            .generator(generator)

        TestHost.run(source, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("$expected$nl$nl", response.getContent())
        }
    }

    // endregion

}
