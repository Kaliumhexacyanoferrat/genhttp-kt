package org.genhttp.modules.reflection

import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.CookieOptions
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseModification
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.cookie

/**
 * Allows a result returned by a service method to be wrapped so that the response can be adjusted.
 */
public interface ResultWrapper {

    /**
     * The actual result of the method.
     */
    public val payload: Any?

    /**
     * Applies the modifications of the wrapper to the given response.
     */
    public fun apply(builder: ResponseBuilder)

}
