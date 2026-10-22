package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.InterceptWith
import org.genhttp.modules.reflection.InterceptionResult
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class InterceptionTests {

    // region Supporting data structures

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    @InterceptWith(MyInterceptor::class)
    annotation class MyAttribute(val command: String)

    class MyInterceptor : OperationInterceptor {

        var command: String? = null
            private set

        override fun configure(attribute: Annotation) {
            if (attribute is MyAttribute) {
                command = attribute.command
            }
        }

        override suspend fun intercept(request: Request, operation: Operation, arguments: Map<ByteString, Any?>): InterceptionResult? {
            if (command == "intercept") {
                val result = InterceptionResult("Nah")
                result.status(ResponseStatus.Forbidden)

                return result
            }

            if (command == "throw") {
                throw ProviderException(ResponseStatus.Forbidden, "Nah")
            }

            return null
        }

    }

    // endregion

    // region Handlers (lambdas cannot be annotated, so they are declared as functions)

    @MyAttribute("intercept")
    fun intercepted(q: Int?): Int = 42

    @MyAttribute("pass")
    fun passed(): Int = 42

    @MyAttribute("throw")
    fun thrown(): Int = 42

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testInterception(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create().get(this@InterceptionTests::intercepted).executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Forbidden)

            assertEquals("Nah", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testPassThrough(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create().get(this@InterceptionTests::passed).executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testException(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create().get(this@InterceptionTests::thrown).executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Forbidden)

            AssertX.contains("Nah", response.getContent())
        }
    }

    // endregion

}
