package org.genhttp.engine.ioxide.infrastructure.endpoints

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.ioxide.infrastructure.IoxideServer
import org.genhttp.engine.shared.infrastructure.EndPointConfiguration
import org.genhttp.engine.shared.infrastructure.Protocols

/**
 * The endpoints of a server (`EndPointCollection` in GenHTTP), refusing the combinations the engine cannot serve.
 */
internal class ServerEndPoints private constructor(
    private val endPoints: MutableList<SocketEndPoint>
) : EndPointCollection, List<EndPoint> by endPoints {

    constructor(server: IoxideServer, configuration: List<EndPointConfiguration>) : this(mutableListOf<SocketEndPoint>()) {
        configuration.groupBy { it.port }.entries.firstOrNull { it.key != 0 && it.value.size > 1 }?.let { duplicate ->
            throw UnsupportedOperationException(
                "Port ${duplicate.key} was bound ${duplicate.value.size} times. A connection is matched to its endpoint by the port it arrived on, " +
                        "so each port carries one endpoint."
            )
        }

        val dualStack = configuration.firstOrNull()?.dualStack ?: true

        configuration.filter { it.dualStack != dualStack }.takeIf { it.isNotEmpty() }?.let { disagreeing ->
            throw UnsupportedOperationException(
                "The ioxide engine binds every endpoint with one dual-stack mode, taken from the first one bound (port ${configuration[0].port}, " +
                        "DualStack = $dualStack). These ask for the other: ${disagreeing.joinToString { it.port.toString() }}."
            )
        }

        for (config in configuration) {
            endPoints.add(build(server, config))
        }
    }

    // region Get-/Setters

    /**
     * The endpoints of the server.
     */
    val sockets: List<SocketEndPoint>
        get() = endPoints

    /**
     * The dual-stack mode of the endpoints, which is the same for all of them.
     */
    val dualStack: Boolean
        get() = endPoints.firstOrNull()?.dualStack ?: true

    // endregion

    // region Functionality

    /**
     * The endpoint a connection arrived on (a scan, as a server binds a handful).
     */
    fun forPort(port: Int): SocketEndPoint {
        for (endPoint in endPoints) {
            if (endPoint.port == port) {
                return endPoint
            }
        }

        throw IllegalStateException("A connection arrived on port $port, which no endpoint is bound to")
    }

    private fun build(server: IoxideServer, configuration: EndPointConfiguration): SocketEndPoint {
        // the shared validator settles what this configuration can actually serve (refusing a port left with nothing)
        val protocols = Protocols.validate(configuration)

        if (!protocols.hasFlag(HttpProtocols.Http1)) {
            throw UnsupportedOperationException("The ioxide engine does only support HTTP/1.1")
        }

        val security = configuration.security
            ?: return InsecureEndPoint(server, configuration.address, configuration.port, configuration.dualStack)

        return SecureEndPoint(server, configuration.address, configuration.port, configuration.dualStack, security)
    }

    // endregion

}
