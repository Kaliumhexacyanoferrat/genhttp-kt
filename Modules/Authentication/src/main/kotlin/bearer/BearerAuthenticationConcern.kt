package org.genhttp.modules.authentication.bearer

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyConverter
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

import kotlinx.coroutines.future.await

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.setUser

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Key
import java.text.ParseException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import kotlin.coroutines.cancellation.CancellationException

// region Supporting data structures

/**
 * The parts of the OpenID Connect discovery document we are interested in.
 */
internal class OpenIdConfiguration(val keySetUrl: String?) {

    companion object {

        /**
         * Reads the discovery document (the key set url is stored in `jwks_uri`).
         */
        fun parse(json: String): OpenIdConfiguration = OpenIdConfiguration(JSONObjectUtils.parse(json)["jwks_uri"] as? String)

    }

}

/**
 * Thrown if a token does not pass the validation (the counterpart of `SecurityTokenValidationException`).
 */
internal class TokenValidationException(message: String) : Exception(message)

// endregion

internal class BearerAuthenticationConcern(
    override val content: Handler,
    private val validationOptions: TokenValidationOptions
) : Concern {

    private val issuerKeys = AtomicReference<Collection<Key>?>(null)

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val authHeader = request.header.headers.getEntry("Authorization")
            ?: throw ProviderException(ResponseStatus.Unauthorized, "This endpoint requires authorization")

        if (!authHeader.startsWith("Bearer ")) {
            throw ProviderException(ResponseStatus.Unauthorized, "This endpoint requires bearer token authentication")
        }

        val tokenString = authHeader.substring(7)

        val jwt = try {
            SignedJWT.parse(tokenString)
        } catch (_: ParseException) {
            throw ProviderException(ResponseStatus.BadRequest, "Malformed authentication token")
        }

        val issuer = validationOptions.issuer

        val audience = validationOptions.audience

        if (issuer != null && issuerKeys.get() == null) {
            val customKeyResolver = validationOptions.customKeyResolver

            issuerKeys.set(
                if (customKeyResolver != null) {
                    customKeyResolver(jwt)
                } else {
                    fetchSigningKeys(issuer)
                }
            )
        }

        try {
            if (issuer != null) {
                validateSignature(jwt, issuerKeys.get() ?: emptyList())
            }

            val claims = try {
                jwt.jwtClaimsSet
            } catch (e: ParseException) {
                throw TokenValidationException("Unable to read the claims of the token: ${e.message}")
            }

            if (validationOptions.lifetime) {
                validateLifetime(claims)
            }

            if (audience != null) {
                validateAudience(claims, audience)
            }

            if (issuer != null) {
                validateIssuer(claims, issuer)
            }

            validationOptions.customValidator?.invoke(jwt)

            val userMapping = validationOptions.userMapping

            if (userMapping != null) {
                val user = userMapping(request, jwt)

                if (user != null) {
                    request.setUser(user)
                }
            }

            return content.handle(request)
        } catch (ex: TokenValidationException) {
            throw ProviderException(ResponseStatus.Unauthorized, "Authorization failed: ${ex.message}", ex)
        }
    }

    // endregion

    // region Validation

    private fun validateSignature(jwt: SignedJWT, keys: Collection<Key>) {
        if (keys.isEmpty()) {
            throw TokenValidationException("Signature validation failed. No security keys were provided to validate the signature.")
        }

        val factory = DefaultJWSVerifierFactory()

        for (key in keys) {
            try {
                if (jwt.verify(factory.createJWSVerifier(jwt.header, key))) {
                    return
                }
            } catch (_: JOSEException) {
                // the key cannot be used to verify the token, try the next one
            }
        }

        throw TokenValidationException("Signature validation failed. Unable to match any of the signing keys to the signature of the token.")
    }

    private fun validateLifetime(claims: JWTClaimsSet) {
        val expires = claims.expirationTime?.toInstant()
            ?: throw TokenValidationException("Lifetime validation failed. The token is missing an Expiration Time.")

        val notBefore = claims.notBeforeTime?.toInstant()

        if (notBefore != null && notBefore.isAfter(expires)) {
            throw TokenValidationException("Lifetime validation failed. NotBefore '$notBefore' must be before Expires '$expires'.")
        }

        val now = Instant.now()

        if (notBefore != null && notBefore.isAfter(now.plus(ClockSkew))) {
            throw TokenValidationException("Lifetime validation failed. The token is not yet valid. ValidFrom (UTC): '$notBefore', Current time (UTC): '$now'.")
        }

        if (expires.isBefore(now.minus(ClockSkew))) {
            throw TokenValidationException("Lifetime validation failed. The token is expired. ValidTo (UTC): '$expires', Current time (UTC): '$now'.")
        }
    }

    private fun validateAudience(claims: JWTClaimsSet, audience: String) {
        val audiences = claims.audience

        if (audiences.isNullOrEmpty()) {
            throw TokenValidationException("Audience validation failed. The token does not contain an audience.")
        }

        if (audiences.none { it.trimEnd('/') == audience.trimEnd('/') }) {
            throw TokenValidationException("Audience validation failed. Audiences: '${audiences.joinToString(", ")}'. Did not match: '$audience'.")
        }
    }

    private fun validateIssuer(claims: JWTClaimsSet, issuer: String) {
        val tokenIssuer = claims.issuer

        if (tokenIssuer.isNullOrBlank()) {
            throw TokenValidationException("Issuer validation failed. The token does not contain an issuer.")
        }

        if (tokenIssuer != issuer) {
            throw TokenValidationException("Issuer validation failed. Issuer: '$tokenIssuer'. Did not match: '$issuer'.")
        }
    }

    // endregion

    // region Key discovery

    private suspend fun fetchSigningKeys(issuer: String): Collection<Key> {
        try {
            val configUrl = "$issuer/.well-known/openid-configuration"

            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().use { httpClient ->
                val configResponse = getString(httpClient, configUrl)

                val config = OpenIdConfiguration.parse(configResponse)

                val keySetUrl = config.keySetUrl
                    ?: throw IllegalStateException("Unable to discover configuration via '$configUrl'")

                val keyResponse = getString(httpClient, keySetUrl)

                val keySet = JWKSet.parse(keyResponse)

                return KeyConverter.toJavaKeys(keySet.keys.filter { it.keyUse == null || it.keyUse == KeyUse.SIGNATURE })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            throw ProviderException(ResponseStatus.InternalServerError, "Unable to fetch signing issuer signing keys", ex)
        }
    }

    private suspend fun getString(httpClient: HttpClient, url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()

        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        if (response.statusCode() !in 200..299) {
            throw IOException("Response status code does not indicate success: ${response.statusCode()}")
        }

        return response.body()
    }

    // endregion

    private companion object {

        /**
         * The default clock skew of the validation parameters of System.IdentityModel (5 minutes).
         */
        private val ClockSkew: Duration = Duration.ofMinutes(5)

    }

}
