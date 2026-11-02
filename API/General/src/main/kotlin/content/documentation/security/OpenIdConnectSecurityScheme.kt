package org.genhttp.api.content.documentation.security

import java.util.Objects

/**
 * The client authenticates with a token obtained from an OpenID Connect provider.
 *
 * @property discoveryUrl The URL of the discovery document of the provider
 */
public class OpenIdConnectSecurityScheme internal constructor(
    override val name: String,
    override val description: String?,
    public val discoveryUrl: String
) : SecurityScheme() {

    /**
     * Creates a copy of this scheme with the given values changed (a `with` expression upstream).
     */
    public fun copy(
        name: String = this.name,
        description: String? = this.description,
        discoveryUrl: String = this.discoveryUrl
    ): OpenIdConnectSecurityScheme = OpenIdConnectSecurityScheme(name, description, discoveryUrl)

    override fun equals(other: Any?): Boolean =
        other is OpenIdConnectSecurityScheme && name == other.name && description == other.description && discoveryUrl == other.discoveryUrl

    override fun hashCode(): Int = Objects.hash(name, description, discoveryUrl)

    override fun toString(): String = "OpenIdConnectSecurityScheme(name=$name, description=$description, discoveryUrl=$discoveryUrl)"

}
