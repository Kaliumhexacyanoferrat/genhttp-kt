package org.genhttp.modules.security

import org.genhttp.modules.security.cors.CorsPolicyBuilder
import org.genhttp.modules.security.cors.OriginPolicy

/**
 * Entry point to create policies that control the cross-origin resource sharing (CORS) of browsers.
 */
public object CorsPolicy {

    /**
     * Creates a policy that does not restrict browsers from interacting with the requested resources.
     *
     * @param allowAuthorization Indicate if the header Authorization should be in 'Access-Control-Allow-Headers'
     */
    public fun permissive(allowAuthorization: Boolean = true): CorsPolicyBuilder =
        CorsPolicyBuilder().default(
            OriginPolicy(
                null,
                if (allowAuthorization) listOf("*", "Authorization") else null,
                null,
                true,
                86400
            )
        )

    /**
     * Creates a policy that denies access to resources by browsers.
     *
     * You may add more permissive policies for specific origins.
     */
    public fun restrictive(): CorsPolicyBuilder = CorsPolicyBuilder()

}
