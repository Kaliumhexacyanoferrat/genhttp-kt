package org.genhttp.api.content.documentation.security

/**
 * The part of the request an API key is sent with.
 */
public enum class ApiKeyLocation {

    /**
     * The key is sent as an HTTP header.
     */
    Header,

    /**
     * The key is sent as a query parameter.
     */
    Query,

    /**
     * The key is sent as a cookie.
     */
    Cookie

}
