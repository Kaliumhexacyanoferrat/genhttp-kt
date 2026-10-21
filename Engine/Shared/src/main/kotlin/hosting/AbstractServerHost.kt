package org.genhttp.engine.shared.hosting

import kotlinx.coroutines.CompletableDeferred

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.api.infrastructure.SslProtocol
import org.genhttp.engine.shared.infrastructure.CoreRouter
import org.genhttp.engine.shared.infrastructure.EndPointConfiguration
import org.genhttp.engine.shared.infrastructure.SecurityConfiguration
import org.genhttp.engine.shared.infrastructure.ServerCompliance
import org.genhttp.engine.shared.infrastructure.ServerConfiguration
import org.genhttp.engine.shared.infrastructure.logging.ConsoleLoggerFactory
import org.genhttp.engine.shared.infrastructure.logging.RequestLoggingConcernBuilder
import org.genhttp.modules.errorhandling.ErrorHandler

import org.slf4j.ILoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

import java.net.InetAddress
import java.util.concurrent.CountDownLatch

/**
 * Base class for server hosts of the engines (`ServerHost` in GenHTTP).
 *
 * Collects the configuration of the server and manages the lifecycle of the server instance. Engines
 * only need to provide the actual server implementation via [build].
 */
public abstract class AbstractServerHost : ServerHost {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val endPoints = mutableListOf<EndPointConfiguration>()

    private var development = false

    private var handler: Handler? = null

    private var port = 8080

    private var customLoggerFactory: ILoggerFactory? = null

    private var logRequests = true

    // region Get-/Setters

    override var instance: Server? = null
        protected set

    // endregion

    // region Functionality

    override fun bind(address: InetAddress?, port: Int, httpProtocols: HttpProtocols, dualStack: Boolean): ServerHost {
        endPoints.add(EndPointConfiguration(address, validatePort(port), httpProtocols, dualStack, null))
        return this
    }

    override fun bind(
        address: InetAddress?,
        port: Int,
        certificateProvider: CertificateProvider,
        sslProtocols: Set<SslProtocol>,
        certificateValidator: CertificateValidator?,
        httpProtocols: HttpProtocols,
        dualStack: Boolean
    ): ServerHost {
        endPoints.add(EndPointConfiguration(address, validatePort(port), httpProtocols, dualStack, SecurityConfiguration(certificateProvider, sslProtocols, certificateValidator)))
        return this
    }

    override fun development(developmentMode: Boolean): ServerHost {
        development = developmentMode
        return this
    }

    override fun logging(loggerFactory: ILoggerFactory, logRequests: Boolean): ServerHost {
        customLoggerFactory = loggerFactory
        this.logRequests = logRequests && loggerFactory !is NOPLoggerFactory
        return this
    }

    override fun port(port: Int): ServerHost {
        if (port == 0) {
            throw IllegalArgumentException("port")
        }

        this.port = validatePort(port)
        return this
    }

    override fun handler(handler: Handler): ServerHost {
        this.handler = handler
        return this
    }

    override fun add(concern: ConcernBuilder): ServerHost {
        concerns.add(concern)
        return this
    }

    override suspend fun run(): Int {
        try {
            val shutdown = CompletableDeferred<Unit>()
            val stopped = CountDownLatch(1)

            val hook = Thread {
                shutdown.complete(Unit)
                stopped.await()
            }

            Runtime.getRuntime().addShutdownHook(hook)

            try {
                start()
                shutdown.await()
            } finally {
                try {
                    stop()
                } finally {
                    stopped.countDown()

                    if (!shutdown.isCompleted) {
                        // not stopped by the JVM, so the hook is not needed anymore
                        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
                    }
                }
            }

            return 0
        } catch (e: Exception) {
            val current = instance

            if (current != null) {
                current.logging.getLogger(ServerHost::class.java.name).error("Server stopped unexpectedly", e)
            } else {
                e.printStackTrace()
            }

            return -1
        }
    }

    override suspend fun start(): ServerHost {
        stop()

        val server = createInstance()
        instance = server

        val logger = server.logging.getLogger(ServerHost::class.java.name)

        logger.info("Starting server ...")

        server.start()

        logger.info("Server has started")

        return this
    }

    override suspend fun stop(): ServerHost {
        instance?.let {
            it.logging.getLogger(ServerHost::class.java.name).info("Server is shutting down ...")
            it.close()
        }

        instance = null

        return this
    }

    override suspend fun restart(): ServerHost {
        stop()
        start()
        return this
    }

    /**
     * Creates the engine specific server instance for the given configuration.
     *
     * @param config The configuration of the server
     * @param handler The root handler of the server (including the concerns of the engine)
     */
    protected abstract fun build(config: ServerConfiguration, handler: Handler): Server

    private fun createInstance(): Server {
        val handler = handler ?: throw BuilderMissingPropertyException("Handler")

        val endpoints = endPoints.toMutableList()

        if (endpoints.isEmpty()) {
            endpoints.add(EndPointConfiguration(null, port, HttpProtocols.Http1, true, null))
        }

        val logging = customLoggerFactory ?: defaultLoggerFactory

        val config = ServerConfiguration(development, endpoints, logging)

        val concerns = mutableListOf<ConcernBuilder>(
            ServerCompliance.create(),
            ErrorHandler.default()
        )

        concerns.addAll(this.concerns)

        if (logRequests) {
            concerns.add(RequestLoggingConcernBuilder())
        }

        return build(config, CoreRouter(handler, concerns))
    }

    private fun validatePort(port: Int): Int {
        if (port !in 0..65535) {
            throw IllegalArgumentException("Invalid port: $port")
        }

        return port
    }

    // endregion

    private companion object {

        val defaultLoggerFactory: ILoggerFactory by lazy { ConsoleLoggerFactory.createDefault() }

    }

}
