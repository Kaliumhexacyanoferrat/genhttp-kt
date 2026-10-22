package org.genhttp.modules.dependencyinjection

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Represents a dependency injection enabled handler that can be used to
 * serve requests.
 *
 * In contrast to regular handlers there is no handler preparation as
 * the lifecycle of the handler is managed by the dependency injection
 * container.
 */
public interface DependentHandler {

    /**
     * Handles the given request and returns a response, if applicable.
     *
     * Not returning a response causes the server to respond with a not found
     * response code.
     *
     * @param request The request to be handled
     * @return The response to be sent to the requesting client
     */
    public suspend fun handle(request: Request): Response?

}
