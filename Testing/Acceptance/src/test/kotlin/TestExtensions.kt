package org.genhttp.testing.acceptance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.testing.acceptance.utilities.WrappingHandlerBuilder
import org.genhttp.testing.getHeader

import java.net.http.HttpResponse

/**
 * Constants shared by the acceptance tests.
 */
object Constants {

    const val ClientTimeout: Int = 15000

}

/**
 * Wraps the given handler into a builder, so concerns can be added to it.
 */
fun Handler.wrap(): WrappingHandlerBuilder = WrappingHandlerBuilder(this)

/**
 * Returns the ETag sent with the response.
 */
fun HttpResponse<*>.getETag(): String? = getHeader("ETag")

/**
 * Runs the body of a test function within a coroutine (`async Task` test methods in GenHTTP).
 *
 * ```kotlin
 * @MultiEngineTest
 * fun testSomething(engine: ServerEngine) = test {
 *     TestHost.run(handler, engine = engine).use { runner -> ... }
 * }
 * ```
 */
fun test(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking { block() }
