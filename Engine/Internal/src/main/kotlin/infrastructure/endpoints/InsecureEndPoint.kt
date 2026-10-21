package org.genhttp.engine.internal.infrastructure.endpoints

import org.genhttp.api.infrastructure.Server

import java.net.InetAddress
import java.nio.channels.SocketChannel

/**
 * An endpoint serving plain HTTP.
 */
internal class InsecureEndPoint(server: Server, address: InetAddress?, port: Int, dualStack: Boolean) :
    SocketEndPoint(server, address, port, dualStack) {

    override val secure: Boolean
        get() = false

    override suspend fun accept(client: SocketChannel) {
        handle(client, client)
    }

}
