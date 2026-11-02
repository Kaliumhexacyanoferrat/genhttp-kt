package org.genhttp.api.content.documentation.security

import java.util.Objects

/**
 * The client authenticates using an HTTP authentication scheme (such as basic or bearer authentication).
 *
 * @property scheme The name of the HTTP authentication scheme (e.g. "basic")
 * @property bearerFormat For bearer authentication, a hint on the format of the token (e.g. "JWT")
 */
public class HttpSecurityScheme internal constructor(
    override val name: String,
    override val description: String?,
    public val scheme: String,
    public val bearerFormat: String?
) : SecurityScheme() {

    /**
     * Creates a copy of this scheme with the given values changed (a `with` expression upstream).
     */
    public fun copy(
        name: String = this.name,
        description: String? = this.description,
        scheme: String = this.scheme,
        bearerFormat: String? = this.bearerFormat
    ): HttpSecurityScheme = HttpSecurityScheme(name, description, scheme, bearerFormat)

    override fun equals(other: Any?): Boolean =
        other is HttpSecurityScheme && name == other.name && description == other.description && scheme == other.scheme && bearerFormat == other.bearerFormat

    override fun hashCode(): Int = Objects.hash(name, description, scheme, bearerFormat)

    override fun toString(): String = "HttpSecurityScheme(name=$name, description=$description, scheme=$scheme, bearerFormat=$bearerFormat)"

}
