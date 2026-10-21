package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a concern that instructs clients to only use secured connections (HSTS).
 */
public class StrictTransportConcernBuilder : ConcernBuilder {

    private var policy: StrictTransportPolicy? = null

    // region Functionality

    /**
     * Sets the policy to be announced to the clients.
     *
     * @param policy The policy to be announced
     */
    public fun policy(policy: StrictTransportPolicy): StrictTransportConcernBuilder {
        this.policy = policy
        return this
    }

    override fun build(content: Handler): Concern {
        val policy = policy ?: throw BuilderMissingPropertyException("policy")

        return StrictTransportConcern(content, policy)
    }

    // endregion

}
