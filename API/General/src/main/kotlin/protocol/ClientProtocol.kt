package org.genhttp.api.protocol

/**
 * The protocol used by the connected client.
 */
public enum class ClientProtocol {

    /** Plain, non encrypted HTTP. */
    Http,

    /** HTTP via TLS. */
    Https

}
