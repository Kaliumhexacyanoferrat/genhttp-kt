package org.genhttp.api.content.documentation.security

import java.util.Objects

/**
 * The client authenticates by sending a key with the request.
 *
 * @property parameter The name of the header, query parameter or cookie the key is sent with
 * @property location The part of the request the key is sent with
 */
public class ApiKeySecurityScheme internal constructor(
    override val name: String,
    override val description: String?,
    public val parameter: String,
    public val location: ApiKeyLocation
) : SecurityScheme() {

    /**
     * Creates a copy of this scheme with the given values changed (a `with` expression upstream).
     */
    public fun copy(
        name: String = this.name,
        description: String? = this.description,
        parameter: String = this.parameter,
        location: ApiKeyLocation = this.location
    ): ApiKeySecurityScheme = ApiKeySecurityScheme(name, description, parameter, location)

    override fun equals(other: Any?): Boolean =
        other is ApiKeySecurityScheme && name == other.name && description == other.description && parameter == other.parameter && location == other.location

    override fun hashCode(): Int = Objects.hash(name, description, parameter, location)

    override fun toString(): String = "ApiKeySecurityScheme(name=$name, description=$description, parameter=$parameter, location=$location)"

}
