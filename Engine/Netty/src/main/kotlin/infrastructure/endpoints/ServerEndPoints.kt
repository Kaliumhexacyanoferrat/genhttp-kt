package org.genhttp.engine.netty.infrastructure.endpoints

import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.netty.infrastructure.NettyServer
import org.genhttp.engine.shared.infrastructure.EndPointConfiguration

/**
 * The endpoints of a server (`EndPointCollection` in GenHTTP).
 */
internal class ServerEndPoints private constructor(
    private val server: NettyServer,
    private val endPoints: MutableList<EndPoint>
) : EndPointCollection, List<EndPoint> by endPoints, AutoCloseable {

    constructor(server: NettyServer, configuration: Iterable<EndPointConfiguration>) : this(server, mutableListOf<EndPoint>()) {
        for (config in configuration) {
            endPoints.add(build(config))
        }
    }

    private val logger = server.logging.getLogger(ServerEndPoints::class.java.name)

    private var disposed = false

    // region Functionality

    private fun build(configuration: EndPointConfiguration): SocketEndPoint {
        if (!configuration.protocols.hasFlag(HttpProtocols.Http1)) {
            throw UnsupportedOperationException("The Netty engine does only support HTTP/1.1")
        }

        val security = configuration.security
            ?: return InsecureEndPoint(server, configuration.address, configuration.port, configuration.dualStack)

        return SecureEndPoint(server, configuration.address, configuration.port, configuration.dualStack, security)
    }

    suspend fun start(group: EventLoopGroup, loops: List<EventLoop>) {
        for (endPoint in endPoints) {
            (endPoint as? SocketEndPoint)?.start(group, loops)
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
