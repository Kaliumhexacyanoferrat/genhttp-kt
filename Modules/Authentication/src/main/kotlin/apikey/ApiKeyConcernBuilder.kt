package org.genhttp.modules.authentication.apikey

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getEntry

/**
 * Configures a concern that checks API keys sent by the client.
 */
public class ApiKeyConcernBuilder : ConcernBuilder {

    private var authenticator: (suspend (Request, String) -> User?)? = null

    private var keyExtractor: ((Request) -> String?)? = { request -> request.header.headers.getEntry("X-API-Key") }

    // region Functionality

    /**
     * Configures the logic that is used to extract an API key from an incoming request (e.g. by reading
     * a header value).
     *
     * @param keyExtractor The logic to be executed to fetch an API key from a request
     */
    public fun extractor(keyExtractor: (Request) -> String?): ApiKeyConcernBuilder {
        this.keyExtractor = keyExtractor
        return this
    }

    /**
     * Configures the handler to read the API key from the specified HTTP header.
     *
     * @param headerName The name of the header to be read from the request
     */
    public fun withHeader(headerName: String): ApiKeyConcernBuilder {
        keyExtractor = { request -> request.header.headers.getEntry(headerName) }
        return this
    }

    /**
     * Configures the handler to read the API key from the specified query parameter.
     *
     * @param parameter The name of the query parameter to be read from the request
     */
    public fun withQueryParameter(parameter: String): ApiKeyConcernBuilder {
        keyExtractor = { request -> request.header.query.getEntry(parameter) }
        return this
    }

    /**
     * Configures the logic that checks whether a given API key is valid.
     *
     * @param authenticator The logic to be executed to authenticate a request
     */
    public fun authenticator(authenticator: suspend (Request, String) -> User?): ApiKeyConcernBuilder {
        this.authenticator = authenticator
        return this
    }

    /**
     * Configures the handler to accept any of the given API keys.
     *
     * @param allowedKeys The keys which are allowed to access the content secured by the concern
     */
    public fun keys(vararg allowedKeys: String): ApiKeyConcernBuilder {
        val keySet = allowedKeys.toHashSet()

        authenticator = { _, key -> if (keySet.contains(key)) ApiKeyUser(key) else null }

        return this
    }

    override fun build(content: Handler): Concern {
        val keyExtractor = keyExtractor ?: throw BuilderMissingPropertyException("KeyExtractor")

        val authenticator = authenticator ?: throw BuilderMissingPropertyException("Authenticator")

        return ApiKeyConcern(content, keyExtractor, authenticator)
    }

    // endregion

}
