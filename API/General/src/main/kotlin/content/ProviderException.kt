package org.genhttp.api.content

import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus

/**
 * If thrown by a content provider or router, the server will return the specified HTTP response status
 * to the client instead of indicating a server error.
 *
 * @property status The status to be returned to the client
 * @property modifications Modifications to be applied to the generated HTTP response
 */
public class ProviderException(
    public val status: ResponseStatus,
    message: String,
    cause: Throwable? = null,
    public val modifications: (ResponseBuilder.() -> Unit)? = null
) : RuntimeException(message, cause) {

    /**
     * Raises an exception that allows the server to derive a HTTP response status from.
     *
     * @param status The status of the HTTP response to be set
     * @param message The error message to return to the client
     * @param modifications The modifications to be applied to the generated response
     */
    public constructor(status: ResponseStatus, message: String, modifications: ResponseBuilder.() -> Unit) :
            this(status, message, null, modifications)

}
