package org.genhttp.api.content

import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Content provider that is able to handle a request and return a HTTP response to it.
 */
public interface Handler {

    /**
     * Invoked to perform computation heavy or IO bound work that initializes the handler before handling
     * the first requests. Intended to keep the response time for the first requests low.
     *
     * Handlers should relay this call to dependent child handlers to initialize the whole handler chain.
     * May be called multiple times depending on the setup the handler is used in.
     *
     * @param server The server instance running the handler
     */
    public suspend fun prepare(server: Server)

    /**
     * Handles the given request and returns a response, if applicable.
     *
     * Not returning a response causes the server to respond with a not found response code.
     *
     * @param request The request to be handled
     * @return The response to be sent to the requesting client
     */
    public suspend fun handle(request: Request): Response?

    /**
     * Anchor for factory functions provided by modules (e.g. `Handler.from { ... }`).
     */
    public companion object

}
