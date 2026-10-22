@file:JvmName("ApiKeyAuthenticationExtensions")

package org.genhttp.modules.authentication

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request
import org.genhttp.modules.authentication.apikey.ApiKeyConcernBuilder

/**
 * Adds API key authentication to the handler.
 *
 * @param apiKeyAuth The authentication concern to be added
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.authentication(apiKeyAuth: ApiKeyConcernBuilder): T {
    add(apiKeyAuth)
    return this
}

/**
 * Adds API key authentication to the handler, using the given function to check, whether a key passed by the
 * client is valid.
 *
 * Note that the types of the lambda parameters need to be specified explicitly to distinguish this function
 * from the one of the basic authentication.
 *
 * @param authenticator The function to be invoked to determine, whether the given string key is valid
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.authentication(authenticator: suspend (Request, String) -> User?): T {
    add(ApiKeyAuthentication.create().authenticator(authenticator))
    return this
}
