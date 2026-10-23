package org.genhttp.engine.netty.infrastructure

import io.netty.channel.EventLoop
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.context.ConnectionPool
import org.genhttp.engine.netty.infrastructure.endpoints.ServerEndPoints
import org.genhttp.engine.shared.infrastructure.ServerConfiguration
import org.genhttp.engine.shared.types.MapPropertyBag

import org.slf4j.ILoggerFactory

import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

import kotlin.time.Duration.Companion.seconds

/**
 * The server implementation of the Netty engine (`Server` of the ioxide engine in GenHTTP).
 *
 * The server runs a group of event loops (one per CPU core by default). Every connection is handled by a coroutine
 * on the loop that accepted it, timers of the coroutine are scheduled on this loop as well (see
 * [EventLoopDispatcher]).
 */
internal class NettyServer(
    private val configuration: ServerConfiguration,
    override val handler: Handler,
    private val onEventLoopStart: ((EventLoop) -> Unit)?,
    val options: EngineOptions
) : Server {

    /**
     * The transport the event loops run on.
     */
    val transports: Transports = Transports.resolve(options.eventLoop.transport)

    private val scope = CoroutineScope(SupervisorJob())

    private val _endPoints = ServerEndPoints(this, configuration.endPoints)

    private val _properties = MapPropertyBag()

    private val logger = configuration.logging.getLogger(NettyServer::class.java.name)

    private var group: MultiThreadIoEventLoopGroup? = null

    // read-only after the start, so it can be read by all loops
    private var loops: Map<EventLoop, LoopState> = emptyMap()

    @Volatile
    private var disposed = false

    // region Get-/Setters

    override val serverEngine: ServerEngine
        get() = ServerEngine.Netty

    override val version: String = NettyServer::class.java.`package`?.implementationVersion ?: "(n/a)"

    override val running: Boolean
        get() = !disposed

    override val development: Boolean
        get() = configuration.developmentMode

    override val properties: PropertyBag
        get() = _properties

    override val logging: ILoggerFactory
        get() = configuration.logging

    override val endPoints: EndPointCollection
        get() = _endPoints

    // endregion

    // region Functionality

    // the lifecycle runs on a dispatcher, so that callers without one do not continue on an event loop (which would
    // block it) after awaiting the futures of Netty
    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        prepareHandler()

        val group = MultiThreadIoEventLoopGroup(options.eventLoop.count, DefaultThreadFactory("genhttp-netty", true), transports.ioHandlerFactory)

        this@NettyServer.group = group

        try {
            val loops = group.map { it as EventLoop }

            this@NettyServer.loops = IdentityHashMap<EventLoop, LoopState>().apply {
                for (loop in loops) {
                    put(loop, LoopState(EventLoopDispatcher(loop), ConnectionPool(this@NettyServer, loop)))
                }
            }

            onEventLoopStart?.let { hook ->
                for (started in loops.map { loop -> loop.submit { hook(loop) } }) {
                    started.awaitDone().cause()?.let { throw it }
                }
            }

            _endPoints.start(group, loops)
        } catch (e: Throwable) {
            _endPoints.close()
            shutdown(group)
            throw e
        }
    }

    private suspend fun prepareHandler() {
        try {
            val start = System.nanoTime()

            handler.prepare(this)

            val elapsed = (System.nanoTime() - start) / 1_000_000.0

            logger.info("Prepared handlers in {} ms", String.format(Locale.ROOT, "%.2f", elapsed))
        } catch (e: Exception) {
            logger.error("Failed to prepare the handler chain", e)
        }
    }

    /**
     * Handles a connection by running the given function as a coroutine on the event loop of the connection.
     */
    fun launch(loop: EventLoop, block: suspend CoroutineScope.() -> Unit) {
        scope.launch(dispatcher(loop), block = block)
    }

    /**
     * The dispatcher running coroutines on the given event loop.
     */
    fun dispatcher(loop: EventLoop): EventLoopDispatcher = loops[loop]?.dispatcher ?: EventLoopDispatcher(loop)

    /**
     * The pool of the connection state of the given event loop.
     */
    fun connections(loop: EventLoop): ConnectionPool =
        loops[loop]?.connections ?: throw IllegalStateException("The event loop does not belong to the server")

    /**
     * What the server keeps per event loop.
     */
    private class LoopState(val dispatcher: EventLoopDispatcher, val connections: ConnectionPool)

    // endregion

    // region Lifecycle

    override suspend fun close() {
        if (!disposed) {
            disposed = true

            // stops accepting new connections
            _endPoints.close()

            // closes the connections (the drivers waiting for requests end with them)
            withContext(NonCancellable + Dispatchers.IO) {
                closeConnections()
            }

            // stops the handlers that are suspended
            scope.cancel()

            // closes the remaining connections and stops the threads of the loops (completes even if the caller is
            // a handler of this server, which is cancelled above)
            val group = group ?: return

            this.group = null

            withContext(NonCancellable + Dispatchers.IO) {
                shutdown(group)
            }
        }
    }

    private suspend fun closeConnections() {
        val closing = loops.map { (loop, state) ->
            try {
                loop.submit { state.connections.closeAll() }
            } catch (_: RejectedExecutionException) {
                // the loop has been shut down already
                null
            }
        }

        withTimeoutOrNull(ShutdownTimeout) {
            for (future in closing) {
                future?.awaitDone()
            }
        }
    }

    private suspend fun shutdown(group: MultiThreadIoEventLoopGroup) {
        val termination = group.shutdownGracefully(0, ShutdownTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        withTimeoutOrNull(ShutdownTimeout * 2) {
            termination.awaitDone()
        }
    }

    // endregion

    private companion object {

        val ShutdownTimeout = 5.seconds

    }

}
