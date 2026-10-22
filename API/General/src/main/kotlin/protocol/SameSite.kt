package org.genhttp.api.protocol

/**
 * Controls whether a cookie is sent along with cross-site requests.
 */
public enum class SameSite {

    /** The cookie will only be sent in a first-party context. */
    Strict,

    /** The cookie will be sent along with top-level navigations. */
    Lax,

    /** The cookie will be sent with both cross-site and same-site requests. */
    None

}
