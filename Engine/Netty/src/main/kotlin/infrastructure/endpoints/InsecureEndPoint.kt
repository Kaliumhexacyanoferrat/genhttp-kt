package org.genhttp.engine.netty.infrastructure.endpoints

import org.genhttp.engine.netty.infrastructure.NettyServer

import java.net.InetAddress

/**
 * An endpoint serving plain HTTP.
 */
internal class InsecureEndPoint(server: NettyServer, address: InetAddress?, port: Int, dualStack: Boolean) :
    SocketEndPoint(server, address, port, dualStack) {

    override val secure: Boolean
        get() = false

}
