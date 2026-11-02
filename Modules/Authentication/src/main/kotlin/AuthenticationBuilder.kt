package org.genhttp.modules.authentication

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.documentation.security.SecurityScheme

/**
 * A protocol for builders that create authentication concerns, allowing to configure how the authentication
 * mechanism is presented to the consumers of the API (e.g. within a generated OpenAPI specification).
 *
 * @param T The builder type to be returned
 */
public interface AuthenticationBuilder<out T : AuthenticationBuilder<T>> : ConcernBuilder {

    /**
     * Describes how clients obtain their credentials (e.g. where to request an API key).
     *
     * The description is added to the scheme derived from the configuration of the concern or to the scheme
     * passed to [documentation] (with a scheme), regardless of the order of the calls.
     *
     * @param description The description to be shown to the consumers of the API (may contain Markdown)
     */
    public fun documentation(description: String): T

    /**
     * Documents the authentication mechanism as the given scheme, instead of the one derived from the
     * configuration of the concern.
     *
     * Use this if the concern cannot derive the mechanism on its own (e.g. if credentials are read by custom
     * logic) or to document it differently (e.g. to point to the OpenID Connect provider issuing bearer tokens,
     * see [SecurityScheme.openIdConnect]).
     *
     * @param scheme The scheme to be shown to the consumers of the API
     */
    public fun documentation(scheme: SecurityScheme): T

}
