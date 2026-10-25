package org.genhttp.engine.ioxide.infrastructure.endpoints

import org.genhttp.engine.ioxide.infrastructure.IoxideServer

import java.net.InetAddress

/**
 * An endpoint serving plain HTTP.
 */
internal class InsecureEndPoint(server: IoxideServer, address: InetAddress?, port: Int, dualStack: Boolean) :
    SocketEndPoint(server, address, port, dualStack) {

    override val secure: Boolean
        get() = false

}
