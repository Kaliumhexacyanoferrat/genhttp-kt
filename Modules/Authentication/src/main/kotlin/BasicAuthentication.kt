package org.genhttp.modules.authentication

import org.genhttp.api.content.authentication.User
import org.genhttp.modules.authentication.basic.BasicAuthenticationConcernBuilder
import org.genhttp.modules.authentication.basic.BasicAuthenticationKnownUsersBuilder

/**
 * Allows basic authentication to be added to a handler instance as a concern.
 */
public object BasicAuthentication {

    internal const val DefaultRealm: String = "Restricted Area"

    // region Builder

    /**
     * Creates a basic authentication concern that will use the given lambda to check, whether a user is allowed
     * to access the restricted area.
     *
     * @param authenticator The lambda to be evaluated (receives the user name and the password)
     * @param realm The name of the realm returned to the client
     * @return The newly created basic authentication concern
     */
    public fun create(authenticator: suspend (String, String) -> User?, realm: String = DefaultRealm): BasicAuthenticationConcernBuilder =
        BasicAuthenticationConcernBuilder().handler(authenticator).realm(realm)

    /**
     * Creates a basic authentication concern that stores credentials in memory and can be used for quick
     * development purposes.
     *
     * @param realm The name of the realm returned to the client
     * @return The newly created basic authentication concern
     */
    public fun create(realm: String = DefaultRealm): BasicAuthenticationKnownUsersBuilder =
        BasicAuthenticationKnownUsersBuilder().realm(realm)

    // endregion

}
