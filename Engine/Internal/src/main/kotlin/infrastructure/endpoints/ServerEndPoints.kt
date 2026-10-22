package org.genhttp.engine.internal.infrastructure.endpoints

import kotlinx.coroutines.CoroutineScope

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.internal.infrastructure.ConnectionWatchdog
import org.genhttp.engine.shared.infrastructure.EndPointConfiguration

/**
 * The endpoints of a server (`EndPointCollection` in GenHTTP).
 */
internal class ServerEndPoints private constructor(
    private val server: Server,
    private val watchdog: ConnectionWatchdog,
    private val endPoints: MutableList<EndPoint>
) : EndPointCollection, List<EndPoint> by endPoints, AutoCloseable {

    constructor(server: Server, watchdog: ConnectionWatchdog, configuration: Iterable<EndPointConfiguration>) :
            this(server, watchdog, mutableListOf<EndPoint>()) {
        for (config in configuration) {
            endPoints.add(build(config))
        }
    }

    private val logger = server.logging.getLogger(ServerEndPoints::class.java.name)

    private var disposed = false

    // region Functionality

    private fun build(configuration: EndPointConfiguration): SocketEndPoint {
        if (!configuration.protocols.hasFlag(HttpProtocols.Http1)) {
            throw UnsupportedOperationException("The internal engine does only support HTTP/1.1")
        }

        val security = configuration.security
            ?: return InsecureEndPoint(server, watchdog, configuration.address, configuration.port, configuration.dualStack)

        return SecureEndPoint(server, watchdog, configuration.address, configuration.port, configuration.dualStack, security)
    }

    fun start(scope: CoroutineScope) {
        for (endPoint in endPoints) {
            (endPoint as? SocketEndPoint)?.start(scope)
        }
    }

    // endregion

    // region Lifecycle

    override fun close() {
        if (!disposed) {
            for (endPoint in endPoints) {
                try {
                    endPoint.close()
                } catch (e: Exception) {
                    logger.warn("Failed to dispose endpoint", e)
                }
            }

            endPoints.clear()
            disposed = true
        }
    }

    // endregion

}
