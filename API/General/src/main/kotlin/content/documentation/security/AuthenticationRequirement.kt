package org.genhttp.api.content.documentation.security

/**
 * Requires the client to authenticate with any one of the given schemes.
 *
 * @property anyOf The schemes the client may choose from
 */
public data class AuthenticationRequirement(val anyOf: List<SecurityScheme>)
