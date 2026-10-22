package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Result
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import java.io.ByteArrayInputStream
import java.io.InputStream

import kotlin.test.assertEquals

class ResultTests {

    // region Supporting data structures

    data class MyPayload(val message: String)

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testResponseCanBeModified(engine: ServerEngine, mode: ExecutionMode) = test {
        val result = Result(MyPayload("Hello World!"))
            .status(ResponseStatus.Created)
            .connection(Connection.Close)
            .header("X-Custom", "Value")
            .cookie("Cookie", "Value")

        val inline = Inline.create()
            .get { result }
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Created)

            assertEquals("Value", response.getHeader("X-Custom"))

            assertEquals("Cookie=Value", response.getHeader("Set-Cookie"))

            assertEquals("close", response.getHeader("Connection")?.lowercase())
        }
    }

    @MultiEngineFrameworkTest
    fun testStreamsCanBeWrapped(engine: ServerEngine, mode: ExecutionMode) = test {
        val stream: InputStream = ByteArrayInputStream("Hello World!".toByteArray())

        val inline = Inline.create()
            .get { Result(stream).status(ResponseStatus.Created) }
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Created)

            assertEquals("Hello World!", response.getContent())
        }
    }

    // endregion

}
