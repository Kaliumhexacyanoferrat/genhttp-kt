package org.genhttp.modules.authentication

import org.genhttp.modules.authentication.apikey.ApiKeyConcernBuilder

/**
 * Allows API key authentication to be added to a handler instance as a concern.
 */
public object ApiKeyAuthentication {

    // region Builder

    /**
     * Creates a customizable API key authentication handler that will read the key from the HTTP header
     * named "X-API-Key".
     */
    public fun create(): ApiKeyConcernBuilder = ApiKeyConcernBuilder()

    // endregion

}
