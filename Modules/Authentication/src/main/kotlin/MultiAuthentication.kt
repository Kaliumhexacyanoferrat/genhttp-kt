package org.genhttp.modules.authentication

import org.genhttp.modules.authentication.multi.MultiAuthenticationConcernBuilder

/**
 * Allows to combine multiple authentication mechanisms.
 */
public object MultiAuthentication {

    // region Builder

    /**
     * Creates a authentication handler that will use underlying handlers to authenticate the request.
     */
    public fun create(): MultiAuthenticationConcernBuilder = MultiAuthenticationConcernBuilder()

    // endregion

}
