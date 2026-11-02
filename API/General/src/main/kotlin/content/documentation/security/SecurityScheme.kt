package org.genhttp.api.content.documentation.security

/**
 * Describes how a client authenticates against an API, so this mechanism can be presented to the consumers of the
 * API (e.g. as a security scheme within a generated OpenAPI specification).
 *
 * Use the factory functions (such as [apiKey]) to create an instance. The name of the scheme can be changed by
 * copying the scheme, e.g. `scheme.copy(name = "partnerKey")` (a `with` expression upstream).
 */
public sealed class SecurityScheme {

    // region Get-/Setters

    /**
     * The name the scheme is listed with (e.g. "apiKey").
     */
    public abstract val name: String

    /**
     * Tells the consumers of the API how to authenticate (e.g. where to obtain credentials). May be rendered as
     * Markdown.
     */
    public abstract val description: String?

    // endregion

    // region Factories

    public companion object {

        /**
         * The client authenticates by sending a key with the request.
         *
         * @param parameter The name of the header, query parameter or cookie the key is sent with
         * @param location The part of the request the key is sent with
         * @param description Tells the consumers how to authenticate
         */
        public fun apiKey(parameter: String, location: ApiKeyLocation = ApiKeyLocation.Header, description: String? = null): ApiKeySecurityScheme =
            ApiKeySecurityScheme("apiKey", description, parameter, location)

        /**
         * The client authenticates with a user name and a password (HTTP basic authentication).
         *
         * @param description Tells the consumers how to authenticate
         */
        public fun basic(description: String? = null): HttpSecurityScheme =
            HttpSecurityScheme("basic", description, "basic", null)

        /**
         * The client authenticates with a token sent as a bearer token in the "Authorization" header.
         *
         * @param format A hint on the format of the token (e.g. "JWT")
         * @param description Tells the consumers how to authenticate
         */
        public fun bearer(format: String? = null, description: String? = null): HttpSecurityScheme =
            HttpSecurityScheme("bearer", description, "bearer", format)

        /**
         * The client authenticates using the given HTTP authentication scheme (e.g. "digest").
         *
         * @param scheme The name of the HTTP authentication scheme
         * @param description Tells the consumers how to authenticate
         */
        public fun http(scheme: String, description: String? = null): HttpSecurityScheme =
            HttpSecurityScheme(scheme.lowercase(), description, scheme, null)

        /**
         * The client authenticates with a token obtained from an OpenID Connect provider.
         *
         * @param discoveryUrl The URL of the discovery document of the provider (ending with ".well-known/openid-configuration")
         * @param description Tells the consumers how to authenticate
         */
        public fun openIdConnect(discoveryUrl: String, description: String? = null): OpenIdConnectSecurityScheme =
            OpenIdConnectSecurityScheme("openIdConnect", description, discoveryUrl)

    }

    // endregion

}
