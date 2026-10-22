package org.genhttp.engine.internal.infrastructure.endpoints

import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.internal.infrastructure.ConnectionWatch
import org.genhttp.engine.internal.infrastructure.ConnectionWatchdog

import java.net.InetAddress
import java.nio.channels.SocketChannel

/**
 * An endpoint serving plain HTTP.
 */
internal class InsecureEndPoint(server: Server, watchdog: ConnectionWatchdog, address: InetAddress?, port: Int, dualStack: Boolean) :
    SocketEndPoint(server, watchdog, address, port, dualStack) {

    override val secure: Boolean
        get() = false

    override suspend fun accept(client: SocketChannel, watch: ConnectionWatch) {
        handle(client, client, watch)
    }

}
