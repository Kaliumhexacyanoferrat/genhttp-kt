package org.genhttp.modules.authentication.multi

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.modules.authentication.apikey.ApiKeyConcernBuilder
import org.genhttp.modules.authentication.basic.BasicAuthenticationConcernBuilder
import org.genhttp.modules.authentication.basic.BasicAuthenticationKnownUsersBuilder
import org.genhttp.modules.authentication.bearer.BearerAuthenticationConcernBuilder
import org.genhttp.modules.authentication.clientcertificate.ClientCertificateAuthenticationBuilder

/**
 * Builder for creating a multi-concern authentication handler.
 */
public class MultiAuthenticationConcernBuilder : ConcernBuilder {

    // region Fields

    private val delegatingConcernsBuilders = mutableListOf<ConcernBuilder>()

    // endregion

    // region Functionality

    /**
     * Add nested API concern builder to this multi concern.
     *
     * @param apiKeyBuilder Nested API key concern builder
     */
    public fun add(apiKeyBuilder: ApiKeyConcernBuilder): MultiAuthenticationConcernBuilder = addBuilder(apiKeyBuilder)

    /**
     * Add nested API concern builder to this multi concern.
     *
     * @param basicBuilder Nested Basic concern builder
     */
    public fun add(basicBuilder: BasicAuthenticationConcernBuilder): MultiAuthenticationConcernBuilder = addBuilder(basicBuilder)

    /**
     * Add nested API concern builder to this multi concern.
     *
     * @param basicKnownUsersBuilder Nested Basic Known Users concern builder
     */
    public fun add(basicKnownUsersBuilder: BasicAuthenticationKnownUsersBuilder): MultiAuthenticationConcernBuilder = addBuilder(basicKnownUsersBuilder)

    /**
     * Add nested API concern builder to this multi concern.
     *
     * @param bearerBuilder Nested Bearer concern builder
     */
    public fun add(bearerBuilder: BearerAuthenticationConcernBuilder): MultiAuthenticationConcernBuilder = addBuilder(bearerBuilder)

    /**
     * Add nested API concern builder to this multi concern.
     *
     * @param clientCertificateBuilder Nested Client Certificate concern builder
     */
    public fun add(clientCertificateBuilder: ClientCertificateAuthenticationBuilder): MultiAuthenticationConcernBuilder = addBuilder(clientCertificateBuilder)

    private fun addBuilder(concernBuilder: ConcernBuilder): MultiAuthenticationConcernBuilder {
        delegatingConcernsBuilders.add(concernBuilder)
        return this
    }

    /**
     * Construct the multi concern.
     *
     * @param content The handler to be wrapped by the concern
     */
    override fun build(content: Handler): Concern {
        val delegatingConcerns = delegatingConcernsBuilders.map { it.build(content) }

        if (delegatingConcerns.isEmpty()) throw BuilderMissingPropertyException("Concerns")

        return MultiAuthenticationConcern(content, delegatingConcerns)
    }

    // endregion

}
