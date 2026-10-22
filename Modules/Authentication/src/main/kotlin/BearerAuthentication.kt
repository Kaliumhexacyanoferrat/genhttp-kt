package org.genhttp.modules.authentication

import org.genhttp.modules.authentication.bearer.BearerAuthenticationConcernBuilder

/**
 * Allows to grant access to clients passing a valid JWT bearer token.
 */
public object BearerAuthentication {

    /**
     * Creates a concern that will read an access token from the authorization headers and validate it
     * according to its configuration.
     *
     * @return The newly created concern
     */
    public fun create(): BearerAuthenticationConcernBuilder = BearerAuthenticationConcernBuilder()

}
