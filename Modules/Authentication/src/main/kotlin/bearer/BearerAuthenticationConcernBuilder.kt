package org.genhttp.modules.authentication.bearer

import com.nimbusds.jwt.SignedJWT

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request

import java.security.Key

/**
 * Configures a concern that validates JWT bearer tokens sent by the client.
 */
public class BearerAuthenticationConcernBuilder : ConcernBuilder {

    private val options = TokenValidationOptions()

    // region Functionality

    /**
     * Sets the expected issuer. Tokens that are not issued by this party will be declined.
     *
     * Setting the issuer will cause the concern to download and cache the signing keys that are used to ensure
     * that the party actually issued a token (see [keyResolver]).
     *
     * @param issuer The URL of the expected issuer
     */
    public fun issuer(issuer: String): BearerAuthenticationConcernBuilder {
        options.issuer = issuer
        return this
    }

    /**
     * Sets the expected audience that should be accepted.
     *
     * @param audience The audience to check for
     */
    public fun audience(audience: String): BearerAuthenticationConcernBuilder {
        options.audience = audience
        return this
    }

    /**
     * Adds a custom validator that can analyze the token read from the request and can perform additional checks.
     *
     * This validator will be invoked after the regular checks (such as the issuer) have been performed.
     * If you would like to deny user access within a custom validator, you can throw a [ProviderException].
     *
     * @param validator The custom validator to be used
     */
    public fun validation(validator: suspend (SignedJWT) -> Unit): BearerAuthenticationConcernBuilder {
        options.customValidator = validator
        return this
    }

    /**
     * Adds a custom logic to fetch the signing keys. By default, the keys will be downloaded from the URL
     * returned by the `.well-known` information returned by the issuer.
     *
     * @param keyResolver The logic used to resolve the signing keys for a given incoming token
     */
    public fun keyResolver(keyResolver: suspend (SignedJWT) -> Collection<Key>): BearerAuthenticationConcernBuilder {
        options.customKeyResolver = keyResolver
        return this
    }

    /**
     * Optionally register a function that will compute the user that should be set for the request.
     *
     * The usage of this mechanism allows to inject the user into service methods via the user injector class.
     * Returning null within the delegate will not deny user access - if you would like to prevent such user,
     * you can throw a [ProviderException].
     *
     * @param userMapping The user mapping to be used
     */
    public fun userMapping(userMapping: suspend (Request, SignedJWT) -> User?): BearerAuthenticationConcernBuilder {
        options.userMapping = userMapping
        return this
    }

    /**
     * If enabled, tokens that have expired or are not valid yet are still accepted. This should be used for
     * testing purposes only.
     */
    public fun allowExpired(): BearerAuthenticationConcernBuilder {
        options.lifetime = false
        return this
    }

    override fun build(content: Handler): Concern = BearerAuthenticationConcern(content, options)

    // endregion

}
