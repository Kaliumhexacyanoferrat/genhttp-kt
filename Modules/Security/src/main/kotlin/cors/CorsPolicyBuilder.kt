package org.genhttp.modules.security.cors

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.RequestMethod

/**
 * Builds a concern that applies a cross-origin resource sharing (CORS) policy.
 */
public class CorsPolicyBuilder : ConcernBuilder {

    private val additionalPolicies: MutableMap<String, OriginPolicy?> = HashMap(4)

    private var defaultPolicy: OriginPolicy? = null

    // region Functionality

    /**
     * Sets the default policy to be applied, if no origin is given or no specific policy
     * has been registered for the given origin.
     *
     * @param policy The policy to be applied by default
     */
    public fun default(policy: OriginPolicy?): CorsPolicyBuilder {
        defaultPolicy = policy
        return this
    }

    /**
     * Adds a custom policy for the specified origin.
     *
     * @param origin The origin the policy applies to (e.g. https://example.com)
     * @param policy The policy to be applied (if not set, access will be denied)
     */
    public fun add(origin: String, policy: OriginPolicy?): CorsPolicyBuilder {
        additionalPolicies[origin] = policy
        return this
    }

    /**
     * Adds a custom policy for the specified origin.
     *
     * @param origin The origin the policy applies to (e.g. https://example.com)
     * @param allowedMethods The HTTP methods the client is allowed to access (any, if not given)
     * @param allowedHeaders The headers a client may send to the server (any, if not given)
     * @param exposedHeaders The headers that will be accessible by the client (any, if not given)
     * @param allowCredentials Whether the client is allowed to read credentials from the request
     * @param maxAge The duration in seconds this policy is valid for
     */
    public fun add(
        origin: String,
        allowedMethods: List<RequestMethod>?,
        allowedHeaders: List<String>?,
        exposedHeaders: List<String>?,
        allowCredentials: Boolean,
        maxAge: Long = 86400
    ): CorsPolicyBuilder = add(origin, OriginPolicy(allowedMethods, allowedHeaders, exposedHeaders, allowCredentials, maxAge))

    override fun build(content: Handler): Concern = CorsPolicyHandler(content, defaultPolicy, additionalPolicies)

    // endregion

}
