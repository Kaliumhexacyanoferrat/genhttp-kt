package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.ClientConnection
import org.genhttp.api.protocol.ClientProtocol

import java.net.InetAddress
import java.security.cert.X509Certificate

/**
 * Information about the connected client (`ClientConnection` in GenHTTP).
 */
public class RemoteClientConnection : ClientConnection {

    override var address: InetAddress? = null
        private set

    override var protocol: ClientProtocol? = null
        private set

    override var certificate: X509Certificate? = null
        private set

    /**
     * Sets the information of the currently connected client.
     */
    public fun apply(address: InetAddress?, protocol: ClientProtocol?, certificate: X509Certificate?) {
        this.address = address
        this.protocol = protocol
        this.certificate = certificate
    }

    /**
     * Resets the information so the instance can be reused.
     */
    public fun reset() {
        address = null
        protocol = null
        certificate = null
    }

}
