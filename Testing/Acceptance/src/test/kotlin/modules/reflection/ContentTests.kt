package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Result
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getOptionalContent

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ContentTests {

    @MultiEngineFrameworkTest
    fun testDeserialization(engine: ServerEngine, mode: ExecutionMode) = test {
        val expectation = MyType(42)

        val handler = Inline.create()
            .get { expectation }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals(expectation, response.getContent<MyType>())
        }
    }

    @MultiEngineFrameworkTest
    fun testNull(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create()
            .get { null as MyType? }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertNull(response.getOptionalContent<MyType>())
        }
    }

    @MultiEngineFrameworkTest
    fun testUnsupported(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create()
            .get { Result("Nah") }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertFailsWith<IllegalStateException> {
                response.getOptionalContent<MyType>()
            }
        }
    }

    data class MyType(val id: Int)

}
