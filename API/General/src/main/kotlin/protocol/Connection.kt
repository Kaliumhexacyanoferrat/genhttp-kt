package org.genhttp.api.protocol

/**
 * Specifies how the server should treat the client connection after a response has been sent.
 */
public enum class Connection {

    /** The server will keep the connection open and wait for another request (default). */
    KeepAlive,

    /** The server will close the connection after the response has been sent. */
    Close,

    /**
     * The server will instruct the client to upgrade to another protocol by sending the configured
     * response and will close the connection after the response content has been sent.
     */
    Upgrade

}
