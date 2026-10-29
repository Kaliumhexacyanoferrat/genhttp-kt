package org.genhttp.engine.ioxide.infrastructure

import ioxide.Reactor
import ioxide.ServerConfig
import ioxide.TcpConnection
import ioxide.TcpOptions
import ioxide.tls.TlsService

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.infrastructure.endpoints.BindingException
import org.genhttp.engine.ioxide.infrastructure.endpoints.SecureEndPoint
import org.genhttp.engine.ioxide.infrastructure.endpoints.ServerEndPoints
import org.genhttp.engine.shared.infrastructure.ServerConfiguration
import org.genhttp.engine.shared.types.MapPropertyBag

import org.slf4j.ILoggerFactory

import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import kotlin.time.Duration.Companion.seconds

/**
 * The server implementation of the ioxide engine (`Server` of the ioxide engine in GenHTTP).
 *
 * The server runs one io_uring reactor per thread (one per CPU core by default). Every connection is handled by a
 * coroutine on the reactor that accepted it, timers of the coroutine are submitted to its ring as well (see
 * [ReactorDispatcher]).
 */
internal class IoxideServer(
    private val configuration: ServerConfiguration,
    override val handler: Handler,
    private val onReactorStart: ((Reactor) -> Unit)?,
    val options: EngineOptions
) : Server {

    private val scope = CoroutineScope(SupervisorJob())

    private val _endPoints = ServerEndPoints(this, configuration.endPoints)

    private val _properties = MapPropertyBag()

    private val logger = configuration.logging.getLogger(IoxideServer::class.java.name)

    // set by the reactors as they start, read-only afterward
    private var states: Array<ReactorState?> = emptyArray()

    private var reactors: List<Reactor> = emptyList()

    private var threads: List<Thread> = emptyList()

    @Volatile
    private var disposed = false

    // the reactors have been stopped (also by a start that failed)
    @Volatile
    private var stopped = false

    // region Get-/Setters

    override val serverEngine: ServerEngine
        get() = ServerEngine.Ioxide

    override val version: String = IoxideServer::class.java.`package`?.implementationVersion ?: "(n/a)"

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

    // the lifecycle runs on a dispatcher, so that callers do not wait for the reactors on a thread of their own
    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        prepareHandler()

        if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
            throw UnsupportedOperationException("The ioxide engine runs on Linux only (io_uring), not on ${System.getProperty("os.name")}")
        }

        for (endPoint in _endPoints.sockets) {
            endPoint.probe()
        }

        val count = options.reactor.reactorCount

        val config = buildServerConfig()

        val tls = _endPoints.sockets.filterIsInstance<SecureEndPoint>().mapNotNull { endPoint -> endPoint.tlsOptions()?.let { endPoint.port to it } }

        val states = arrayOfNulls<ReactorState>(count)

        val listening = CountDownLatch(count)

        val failure = AtomicReference<Throwable?>()

        val reactors = List(count) { id ->
            Reactor(id, config).apply {
                onStart = { reactor ->
                    val state = ReactorState(this@IoxideServer, reactor, tls.associate { (port, options) -> port to TlsService.start(reactor, options) })

                    ReactorState.bind(state)

                    reactor.handlerContext = state.dispatcher
                    reactor.addTicker { state.connections.sweepHandshakes(reactor.nowMs) }

                    states[id] = state

                    onReactorStart?.invoke(reactor)

                    listening.countDown()
                }

                onFault = { reactor, e ->
                    if (failure.compareAndSet(null, e) && listening.count > 0) {
                        // failed to start: reported by start()
                        while (listening.count > 0) {
                            listening.countDown()
                        }
                    } else {
                        logger.error("Reactor {} stopped unexpectedly", reactor.id, e)
                    }
                }

                tcpHandler = { connection -> serve(connection) }
            }
        }

        val threads = reactors.map { reactor -> Thread.ofPlatform().name("ioxide-genhttp-${reactor.id}").daemon(true).unstarted(reactor::run) }

        this@IoxideServer.states = states
        this@IoxideServer.reactors = reactors
        this@IoxideServer.threads = threads

        threads.forEach(Thread::start)

        val started = listening.await(StartTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        val cause = failure.get() ?: if (!started) IllegalStateException("The reactors did not start within $StartTimeout") else null

        if (cause != null) {
            stopReactors()

            if (cause.message?.startsWith("bind(") == true) {
                throw BindingException("Failed to bind to the ports ${_endPoints.sockets.joinToString { it.port.toString() }}.", cause)
            }

            throw cause
        }

        for (endPoint in _endPoints.sockets) {
            endPoint.announce()
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

    // the runtime configuration: tuning from the options, ports from the bindings
    private fun buildServerConfig(): ServerConfig {
        val reactor = options.reactor
        val tcp = options.tcp

        val ports = _endPoints.sockets.map { it.port }

        return ServerConfig(
            reactorCount = reactor.reactorCount,
            ringEntries = reactor.ringEntries,
            recvBufferSize = reactor.recvBufferSize,
            recvSlots = reactor.recvSlots,
            dualStack = _endPoints.dualStack,
            tcp = TcpOptions(
                port = ports[0],
                extraPorts = ports.drop(1).toIntArray(),
                listenBacklog = tcp.listenBacklog,
                writeSlabSize = tcp.writeSlabSize,
                poolMax = tcp.poolMax,
                recvQueueEntries = tcp.recvQueueEntries,
                // set explicitly, because ioxide defaults both to 60s and this listener carries upgraded connections
                // too - inheriting that would start closing idle websockets
                readTimeoutMs = tcp.idleTimeoutMs.toLong(),
                sendTimeoutMs = tcp.sendTimeoutMs.toLong()
            )
        )
    }

    // runs the connection as a coroutine of the server on the reactor that accepted it, until it has been served
    private suspend fun serve(connection: TcpConnection) {
        val state = ReactorState.current() ?: return

        val endPoint = _endPoints.forPort(connection.listenerPort)

        val context = state.connections.acquire()

        scope.launch(state.dispatcher, CoroutineStart.UNDISPATCHED) {
            context.serve(connection, endPoint)
        }.join()
    }

    // endregion

    // region Lifecycle

    override suspend fun close() {
        if (disposed) {
            return
        }

        disposed = true

        if (stopped) {
            // the start failed, the reactors have been stopped already
            scope.cancel()
            return
        }

        val started = states.filterNotNull()

        withContext(NonCancellable + Dispatchers.IO) {
            // closes the connections (the drivers waiting for requests end with them)
            val closed = started.map { state ->
                CompletableDeferred<Unit>().also { done ->
                    state.reactor.post {
                        state.connections.closeAll()
                        done.complete(Unit)
                    }
                }
            }

            withTimeoutOrNull(ShutdownTimeout) { closed.awaitAll() }

            // stops the handlers that are suspended
            scope.cancel()

            withTimeoutOrNull(ShutdownTimeout) {
                while (started.any { it.connections.active > 0 }) {
                    delay(5)
                }
            }

            stopReactors()

            if (reactors.isNotEmpty()) {
                awaitReleased()
            }
        }
    }

    // stops the reactors and waits for them, as a ring has to be closed on the thread that created it
    private fun stopReactors() {
        stopped = true

        for (reactor in reactors) {
            reactor.stop()
        }

        for (thread in threads) {
            thread.join(ShutdownTimeout.inWholeMilliseconds)
        }

        for (state in states) {
            state?.dispatcher?.stop()
        }
    }

    // the kernel releases the listeners of a closed ring asynchronously (with the accepts that were pending on them), so
    // the server waits until its ports can be bound again (e.g. by a restart)
    private suspend fun awaitReleased() {
        withTimeoutOrNull(ShutdownTimeout) {
            for (endPoint in _endPoints.sockets) {
                while (!endPoint.released()) {
                    delay(5)
                }
            }
        }
    }

    // endregion

    private companion object {

        val StartTimeout = 30.seconds

        val ShutdownTimeout = 5.seconds

    }

}
