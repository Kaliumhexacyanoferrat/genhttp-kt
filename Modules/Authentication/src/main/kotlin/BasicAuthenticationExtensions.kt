@file:JvmName("BasicAuthenticationExtensions")

package org.genhttp.modules.authentication

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.authentication.User
import org.genhttp.modules.authentication.basic.BasicAuthenticationConcernBuilder
import org.genhttp.modules.authentication.basic.BasicAuthenticationKnownUsersBuilder

/**
 * Adds basic authentication to the handler using the given lambda to check, whether users are allowed to
 * access the restricted area.
 *
 * Note that the types of the lambda parameters need to be specified explicitly to distinguish this function
 * from the one of the API key authentication.
 *
 * @param authenticator The lambda to be evaluated on request (receives the user name and the password)
 * @param realm The name of the realm to be returned to the client
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.authentication(
    authenticator: suspend (String, String) -> User?,
    realm: String = BasicAuthentication.DefaultRealm
): T {
    add(BasicAuthentication.create(authenticator, realm))
    return this
}

/**
 * Adds basic authentication to the handler using the specified concern instance.
 *
 * @param basicAuth The pre-configured concern instance to be used
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.authentication(basicAuth: BasicAuthenticationConcernBuilder): T {
    add(basicAuth)
    return this
}

/**
 * Adds basic authentication to the handler using the specified concern instance.
 *
 * @param basicAuth The pre-configured concern instance to be used
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.authentication(basicAuth: BasicAuthenticationKnownUsersBuilder): T {
    add(basicAuth)
    return this
}
