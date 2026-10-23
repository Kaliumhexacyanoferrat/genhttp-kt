package org.genhttp.testing.acceptance.additional.netty

import io.netty.channel.EventLoop

import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.Host
import org.genhttp.engine.netty.Transport
import org.genhttp.testing.TestHost

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hosts a server on the Netty engine on a free port, like `TestHost` (which creates its hosts with the default
 * options of the engine), but with the given engine options and start hook.
 *
 * The log of the server is captured (see [logging]), requests are not logged.
 */
class NettyRunner(
    handler: Handler,
    options: EngineOptions? = null,
    onEventLoopStart: ((EventLoop) -> Unit)? = null
) : AutoCloseable {

    constructor(handler: HandlerBuilder, options: EngineOptions? = null, onEventLoopStart: ((EventLoop) -> Unit)? = null) :
            this(handler.build(), options, onEventLoopStart)

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
    val host: ServerHost = Host.create(onEventLoopStart, options)
        .handler(handler)
        .port(port)
        .logging(logging, logRequests = false)
        .development()

    /**
     * Starts the server.
     */
    suspend fun start(): NettyRunner {
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
         * Creates and starts a server for the given handler.
         */
        suspend fun run(handler: Handler, options: EngineOptions? = null, onEventLoopStart: ((EventLoop) -> Unit)? = null): NettyRunner =
            NettyRunner(handler, options, onEventLoopStart).start()

        /**
         * Creates and starts a server for the given handler.
         */
        suspend fun run(handler: HandlerBuilder, options: EngineOptions? = null, onEventLoopStart: ((EventLoop) -> Unit)? = null): NettyRunner =
            NettyRunner(handler, options, onEventLoopStart).start()

    }

}

/**
 * Collects the messages logged by a server.
 */
class CapturingLoggerFactory : ILoggerFactory {

    /**
     * A logged message.
     */
    class Entry(val level: Level, val message: String, val throwable: Throwable?)

    private val logged = CopyOnWriteArrayList<Entry>()

    /**
     * The messages logged so far.
     */
    val entries: List<Entry>
        get() = logged.toList()

    /**
     * The text of the messages logged so far.
     */
    val messages: List<String>
        get() = logged.map { it.message }

    override fun getLogger(name: String): Logger = CapturingLogger(name, logged)

    private class CapturingLogger(category: String, private val entries: MutableList<Entry>) : LegacyAbstractLogger() {

        init {
            name = category
        }

        override fun isTraceEnabled(): Boolean = false

        override fun isDebugEnabled(): Boolean = false

        override fun isInfoEnabled(): Boolean = true

        override fun isWarnEnabled(): Boolean = true

        override fun isErrorEnabled(): Boolean = true

        override fun getFullyQualifiedCallerName(): String? = null

        override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<Any?>?, throwable: Throwable?) {
            entries.add(Entry(level, MessageFormatter.arrayFormat(messagePattern, arguments, throwable).message.orEmpty(), throwable))
        }

    }

}

/**
 * The transports that can be requested explicitly on this system (whether io_uring is available or not is checked by
 * `TransportTests`).
 */
fun availableTransports(): List<Transport> = buildList {
    add(Transport.Epoll)
    add(Transport.Nio)

    if (isNativeAvailable("io.netty.channel.uring.IoUring")) {
        add(Transport.IoUring)
    }
}

/**
 * Checks the availability of an optional native library of Netty, such as `io.netty.channel.uring.IoUring` or
 * `io.netty.handler.ssl.OpenSsl`. They are implementation details of the engine and therefore not on the compile
 * classpath of the tests, but on the runtime classpath.
 */
fun isNativeAvailable(library: String): Boolean =
    Class.forName(library).getMethod("isAvailable").invoke(null) as Boolean
