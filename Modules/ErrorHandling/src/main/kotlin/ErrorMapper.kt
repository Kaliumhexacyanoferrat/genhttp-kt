package org.genhttp.modules.errorhandling

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Can be used with the error handling module to generate custom
 * responses for exceptions thrown during request handling.
 *
 * @param T The type of exception to be mapped (others will not be handled)
 */
public interface ErrorMapper<in T : Throwable> {

    /**
     * Generates a HTTP response to be sent for the given exception.
     *
     * @param request The request which caused the error
     * @param handler The handler which catched the exception
     * @param error The actual exception to be mapped
     * @param acceptedFormat The format preferred by the client
     * @return An HTTP response to be sent or null, if the error should be handled as not found by the next error handler in the chain
     */
    public suspend fun map(request: Request, handler: Handler, error: T, acceptedFormat: ByteString?): Response?

    /**
     * Generates a HTTP response for a resource that has not been found.
     *
     * @param request The currently handled request
     * @param handler The inner handler of the error handling concern
     * @param acceptedFormat The format preferred by the client
     * @return An HTTP response to be sent or null, if the error should be handled as not found by the next error handler in the chain
     */
    public suspend fun getNotFound(request: Request, handler: Handler, acceptedFormat: ByteString?): Response?

}
