package org.genhttp.engine.shared.types.body

/**
 * Where the connection stands with regard to the body of the current request.
 */
internal enum class BodyState {

    /**
     * The body has not been opened yet, so the header can be accessed.
     */
    Pending,

    /**
     * The body (if any) can be consumed, the header is only accessible if it has been retained.
     */
    Opened,

    /**
     * The connection has been handed over to another protocol.
     */
    Upgraded

}
