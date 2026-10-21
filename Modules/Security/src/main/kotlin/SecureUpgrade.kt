package org.genhttp.modules.security

/**
 * Specifies the strategy of the server to redirect unsecure requests to HTTPS secured endpoints.
 */
public enum class SecureUpgrade {

    /**
     * The server will not attempt to upgrade requests.
     */
    None,

    /**
     * Clients may request an upgrade via the Upgrade-Insecure-Requests header. Aside from that, the
     * server will not attempt to upgrade insecure requests.
     */
    Allow,

    /**
     * The server will always redirect requests to an insecure endpoint to the HTTPS secured one.
     */
    Force

}
