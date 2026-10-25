package org.genhttp.testing.acceptance.additional.ioxide

import ioxide.Reactor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.Host
import org.genhttp.engine.ioxide.ReactorOptions
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.additional.netty.CapturingLoggerFactory
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Hosts a server on the ioxide engine on a free port, like `TestHost` (which creates its hosts with the default
 * options of the engine), but with the given engine options and start hook.
 *
 * The log of the server is captured (see [logging]), requests are not logged.
 */
class IoxideRunner(
    handler: Handler,
    options: EngineOptions? = reactors(2),
    onReactorStart: ((Reactor) -> Unit)? = null
) : AutoCloseable {

    constructor(handler: HandlerBuilder, options: EngineOptions? = reactors(2), onReactorStart: ((Reactor) -> Unit)? = null) :
            this(handler.build(), options, onReactorStart)

    /**
     * The port of the endpoint added by default (if no other endpoint is bound).
     */
    val port: Int = TestHost.nextPort()

    /**
     * Captures the messages logged by the server.
     */
    val logging: CapturingLoggerFactory = CapturingLoggerFactory()

    /**
     * The host running the server.
     */
    val host: ServerHost = Host.create(onReactorStart, options)
        .handler(handler)
        .port(port)
        .logging(logging, logRequests = false)
        .development()

    /**
     * Starts the server.
     */
    suspend fun start(): IoxideRunner {
        host.start()
        return this
    }

    override fun close() {
        runBlocking {
            host.stop()
        }
    }

    companion object {

        /**
         * Engine options with the given number of reactors (tests do not need one per CPU core).
         */
        fun reactors(count: Int): EngineOptions = EngineOptions(reactor = ReactorOptions(reactorCount = count))

        /**
         * Creates and starts a server for the given handler.
         */
        suspend fun run(handler: Handler, options: EngineOptions? = reactors(2), onReactorStart: ((Reactor) -> Unit)? = null): IoxideRunner =
            IoxideRunner(handler, options, onReactorStart).start()

        /**
         * Creates and starts a server for the given handler.
         */
        suspend fun run(handler: HandlerBuilder, options: EngineOptions? = reactors(2), onReactorStart: ((Reactor) -> Unit)? = null): IoxideRunner =
            IoxideRunner(handler, options, onReactorStart).start()

    }

}

/**
 * The platform the ioxide engine runs on: Linux, the only platform its io_uring runtime exists for.
 */
object IoxidePlatform {

    val supported: Boolean = System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

}

/**
 * Runs the body of a test of the ioxide engine on Linux. Tests do not skip what is not available: on other platforms,
 * the test checks that the engine refuses to start instead.
 */
fun ioxideTest(block: suspend CoroutineScope.() -> Unit): Unit = test {
    if (IoxidePlatform.supported) {
        block()
    } else {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

            assertEquals("The ioxide engine runs on Linux only (io_uring), not on ${System.getProperty("os.name")}", e.message)
        }
    }
}
