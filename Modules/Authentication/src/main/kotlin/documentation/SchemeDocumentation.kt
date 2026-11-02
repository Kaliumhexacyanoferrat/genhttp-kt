package org.genhttp.modules.authentication.documentation

import org.genhttp.api.content.documentation.security.ApiKeySecurityScheme
import org.genhttp.api.content.documentation.security.HttpSecurityScheme
import org.genhttp.api.content.documentation.security.OpenIdConnectSecurityScheme
import org.genhttp.api.content.documentation.security.SecurityScheme

/**
 * The documentation configured by the user on an authentication builder.
 */
internal class SchemeDocumentation {

    var scheme: SecurityScheme? = null

    var description: String? = null

    /**
     * Determines the scheme to document a concern with.
     *
     * @param inferred The scheme derived from the configuration of the concern
     */
    fun resolve(inferred: SecurityScheme): SecurityScheme = describe(scheme ?: inferred)

    /**
     * Determines the scheme to document a concern with, if the concern is not always able to derive the scheme
     * from its configuration.
     *
     * @param inferred The scheme derived from the configuration of the concern, if known
     * @return The scheme to be documented or null, if the mechanism cannot be described
     */
    fun resolveOptional(inferred: SecurityScheme?): SecurityScheme? = (scheme ?: inferred)?.let { describe(it) }

    private fun describe(scheme: SecurityScheme): SecurityScheme {
        val description = description ?: return scheme

        return when (scheme) {
            is ApiKeySecurityScheme -> scheme.copy(description = description)
            is HttpSecurityScheme -> scheme.copy(description = description)
            is OpenIdConnectSecurityScheme -> scheme.copy(description = description)
        }
    }

}
