package org.genhttp.modules.virtualhosting.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.protocol.ByteString

/**
 * Builds a handler that routes requests by the host name sent by the client.
 */
public class VirtualHostRouterBuilder : HandlerBuilder.WithConcerns<VirtualHostRouterBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val hosts = LinkedHashMap<ByteString, HandlerBuilder>()

    private var defaultRoute: HandlerBuilder? = null

    // region Functionality

    /**
     * Registers the handler to be invoked for requests to the given host.
     *
     * @param host The name of the host (without port)
     * @param handler The handler to be invoked for the host
     * @throws IllegalStateException Thrown if a host with the given name has already been added
     */
    public fun add(host: String, handler: HandlerBuilder): VirtualHostRouterBuilder {
        val key = ByteString(host)

        if (hosts.containsKey(key)) {
            throw IllegalStateException("A host with this name has already been added")
        }

        hosts[key] = handler

        return this
    }

    override fun add(concern: ConcernBuilder): VirtualHostRouterBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Sets the handler to be invoked if no host matches the request.
     *
     * @param handler The handler to be used as a fallback
     */
    public fun default(handler: HandlerBuilder): VirtualHostRouterBuilder {
        defaultRoute = handler
        return this
    }

    override fun build(): Handler = Concerns.chain(concerns, VirtualHostRouter(hosts, defaultRoute))

    // endregion

}
