package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme as OpenApiSecurityScheme

import org.genhttp.api.content.documentation.security.ApiKeyLocation
import org.genhttp.api.content.documentation.security.ApiKeySecurityScheme
import org.genhttp.api.content.documentation.security.AuthenticationRequirement
import org.genhttp.api.content.documentation.security.HttpSecurityScheme
import org.genhttp.api.content.documentation.security.OpenIdConnectSecurityScheme
import org.genhttp.api.content.documentation.security.SecurityScheme

/**
 * Converts the authentication requirements declared by concerns and
 * interceptors into security schemes and requirements of a document.
 */
internal object SecurityDocumentation {

    // region Requirements

    /**
     * Expands the given requirements (which all need to be satisfied) into the
     * alternatives expected by the OpenAPI specification and registers the referenced
     * schemes with the document.
     */
    fun getRequirements(document: OpenAPI, requirements: Iterable<AuthenticationRequirement>): List<SecurityRequirement> {
        var alternatives: List<Set<String>> = listOf(emptySet())

        for (requirement in requirements) {
            val names = requirement.anyOf.map { register(document, it) }.distinct()

            alternatives = alternatives.flatMap { alternative -> names.map { alternative + it } }
        }

        return toRequirements(alternatives)
    }

    /**
     * Creates the alternatives that satisfy both of the given ones.
     */
    fun combine(first: Iterable<SecurityRequirement>, second: Iterable<SecurityRequirement>): List<SecurityRequirement> {
        val alternatives = first.flatMap { a -> second.map { b -> a.keys + b.keys } }

        return toRequirements(alternatives)
    }

    private fun toRequirements(alternatives: List<Set<String>>): List<SecurityRequirement> {
        val result = mutableListOf<SecurityRequirement>()

        for (alternative in alternatives) {
            if (result.any { it.keys == alternative }) {
                continue
            }

            val requirement = SecurityRequirement()

            for (name in alternative.sorted()) {
                requirement.addList(name, emptyList())
            }

            result.add(requirement)
        }

        return result
    }

    // endregion

    // region Schemes

    /**
     * Adds the given scheme to the document, if not already present.
     *
     * If the name of the scheme is already used by a different scheme,
     * a number will be appended to the name.
     *
     * @return The name the scheme can be referenced with
     */
    fun register(document: OpenAPI, scheme: SecurityScheme): String {
        val converted = convert(scheme)

        val components = document.components ?: Components().also { document.components = it }

        var i = 1

        while (true) {
            val name = if (i == 1) scheme.name else "${scheme.name}$i"

            val existing = components.securitySchemes?.get(name)

            if (existing == null) {
                components.addSecuritySchemes(name, converted)
                return name
            }

            if (areEqual(existing, converted)) {
                return name
            }

            i++
        }
    }

    private fun convert(scheme: SecurityScheme): OpenApiSecurityScheme = when (scheme) {
        is ApiKeySecurityScheme -> OpenApiSecurityScheme()
            .type(OpenApiSecurityScheme.Type.APIKEY)
            .name(scheme.parameter)
            .`in`(
                when (scheme.location) {
                    ApiKeyLocation.Query -> OpenApiSecurityScheme.In.QUERY
                    ApiKeyLocation.Cookie -> OpenApiSecurityScheme.In.COOKIE
                    ApiKeyLocation.Header -> OpenApiSecurityScheme.In.HEADER
                }
            )
            .description(scheme.description)

        is HttpSecurityScheme -> OpenApiSecurityScheme()
            .type(OpenApiSecurityScheme.Type.HTTP)
            .scheme(scheme.scheme)
            .bearerFormat(scheme.bearerFormat)
            .description(scheme.description)

        is OpenIdConnectSecurityScheme -> OpenApiSecurityScheme()
            .type(OpenApiSecurityScheme.Type.OPENIDCONNECT)
            .openIdConnectUrl(scheme.discoveryUrl)
            .description(scheme.description)
    }

    private fun areEqual(x: OpenApiSecurityScheme, y: OpenApiSecurityScheme): Boolean =
        x.type == y.type &&
            x.name == y.name &&
            x.`in` == y.`in` &&
            x.scheme == y.scheme &&
            x.bearerFormat == y.bearerFormat &&
            x.openIdConnectUrl == y.openIdConnectUrl &&
            x.description == y.description

    // endregion

}
