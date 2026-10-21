package org.genhttp.api.infrastructure

import org.genhttp.api.protocol.ClientProtocol

import java.net.InetAddress
import java.security.cert.X509Certificate

/**
 * The remote client which requests a resource from the server.
 */
public interface ClientConnection {

    /**
     * The IP address of the remotely connected client.
     */
    public val address: InetAddress?

    /**
     * The protocol used by the client to connect to the server.
     */
    public val protocol: ClientProtocol?

    /**
     * The certificate used by the client to connect.
     */
    public val certificate: X509Certificate?

}
