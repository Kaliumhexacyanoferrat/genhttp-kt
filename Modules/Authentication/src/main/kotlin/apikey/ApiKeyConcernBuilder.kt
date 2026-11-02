package org.genhttp.modules.authentication.apikey

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.documentation.security.ApiKeyLocation
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getCookie
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.AuthenticationBuilder
import org.genhttp.modules.authentication.documentation.SchemeDocumentation

/**
 * Configures a concern that checks API keys sent by the client.
 */
public class ApiKeyConcernBuilder : AuthenticationBuilder<ApiKeyConcernBuilder> {

    private val schemeDocumentation = SchemeDocumentation()

    private var authenticator: (suspend (Request, String) -> User?)? = null

    private var keyExtractor: ((Request) -> String?)? = { request -> request.header.headers.getEntry(DefaultHeader) }

    private var inferredScheme: SecurityScheme? = SecurityScheme.apiKey(DefaultHeader)

    // region Functionality

    /**
     * Configures the logic that is used to extract an API key from an incoming request (e.g. by reading
     * a header value).
     *
     * As the concern cannot know where the logic reads the key from, it will not be documented in a generated
     * API specification unless you describe it with [documentation] (passing a scheme).
     *
     * @param keyExtractor The logic to be executed to fetch an API key from a request
     */
    public fun extractor(keyExtractor: (Request) -> String?): ApiKeyConcernBuilder {
        this.keyExtractor = keyExtractor
        inferredScheme = null
        return this
    }

    /**
     * Configures the handler to read the API key from the specified HTTP header.
     *
     * @param headerName The name of the header to be read from the request
     */
    public fun withHeader(headerName: String): ApiKeyConcernBuilder {
        keyExtractor = { request -> request.header.headers.getEntry(headerName) }
        inferredScheme = SecurityScheme.apiKey(headerName)
        return this
    }

    /**
     * Configures the handler to read the API key from the specified query parameter.
     *
     * @param parameter The name of the query parameter to be read from the request
     */
    public fun withQueryParameter(parameter: String): ApiKeyConcernBuilder {
        keyExtractor = { request -> request.header.query.getEntry(parameter) }
        inferredScheme = SecurityScheme.apiKey(parameter, ApiKeyLocation.Query)
        return this
    }

    /**
     * Configures the handler to read the API key from the specified cookie.
     *
     * @param cookie The name of the cookie to be read from the request
     */
    public fun withCookie(cookie: String): ApiKeyConcernBuilder {
        keyExtractor = { request -> request.header.headers.getCookie(cookie) }
        inferredScheme = SecurityScheme.apiKey(cookie, ApiKeyLocation.Cookie)
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

    override fun documentation(description: String): ApiKeyConcernBuilder {
        schemeDocumentation.description = description
        return this
    }

    override fun documentation(scheme: SecurityScheme): ApiKeyConcernBuilder {
        schemeDocumentation.scheme = scheme
        return this
    }

    override fun build(content: Handler): Concern {
        val keyExtractor = keyExtractor ?: throw BuilderMissingPropertyException("KeyExtractor")

        val authenticator = authenticator ?: throw BuilderMissingPropertyException("Authenticator")

        return ApiKeyConcern(content, keyExtractor, authenticator, schemeDocumentation.resolveOptional(inferredScheme))
    }

    // endregion

    private companion object {

        const val DefaultHeader = "X-API-Key"

    }

}
