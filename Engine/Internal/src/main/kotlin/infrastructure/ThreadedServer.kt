package org.genhttp.engine.internal.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.engine.internal.infrastructure.endpoints.ServerEndPoints
import org.genhttp.engine.shared.infrastructure.ServerConfiguration
import org.genhttp.engine.shared.types.MapPropertyBag

import org.slf4j.ILoggerFactory

import java.util.Locale
import java.util.concurrent.Executors

/**
 * The server implementation of the internal engine.
 *
 * Connections are handled as coroutines on a dispatcher that runs every task on a new virtual thread.
 */
internal class ThreadedServer(private val configuration: ServerConfiguration, override val handler: Handler) : Server {

    private val dispatcher: ExecutorCoroutineDispatcher = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("genhttp-", 0).factory()
    ).asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _endPoints = ServerEndPoints(this, configuration.endPoints)

    private val _properties = MapPropertyBag()

    private val logger = configuration.logging.getLogger(ThreadedServer::class.java.name)

    @Volatile
    private var disposed = false

    // region Get-/Setters

    override val serverEngine: ServerEngine
        get() = ServerEngine.Internal

    override val version: String = ThreadedServer::class.java.`package`?.implementationVersion ?: "(n/a)"

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

    override suspend fun start() {
        prepareHandler()
        _endPoints.start(scope)
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

    // endregion

    // region Lifecycle

    override suspend fun close() {
        if (!disposed) {
            disposed = true

            _endPoints.close()

            // terminates idle connections waiting for the next request; the executor is not shut down,
            // so cancelled connections can still complete their cleanup (virtual threads are not pooled)
            scope.cancel()
        }
    }

    // endregion

}
