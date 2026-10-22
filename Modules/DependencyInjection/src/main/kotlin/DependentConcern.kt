package org.genhttp.modules.dependencyinjection

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Represents a dependency injection enabled concern that can be used to
 * serve requests.
 *
 * In contrast to regular concerns there is no concern or content preparation as
 * the lifecycle of the handler is managed by the dependency injection
 * container.
 */
public interface DependentConcern {

    /**
     * Handles the given request and returns a response, if applicable.
     *
     * Not returning a response causes the server to respond with a not found
     * response code.
     *
     * @param content The (initialized) content to be served by the concern
     * @param request The request to be handled
     * @return The response to be sent to the requesting client
     */
    public suspend fun handle(content: Handler, request: Request): Response?

}
