package org.genhttp.modules.redirects.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

import java.net.URI
import java.net.URISyntaxException

/**
 * Builds a handler that redirects the requesting client to another location.
 */
public class RedirectProviderBuilder : HandlerBuilder.WithConcerns<RedirectProviderBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var location: String? = null

    private var temporary: Boolean = false

    // region Functionality

    /**
     * Sets the location the client will be redirected to.
     *
     * @param location The location to redirect the client to (an absolute or relative route)
     */
    public fun location(location: String): RedirectProviderBuilder {
        this.location = location
        return this
    }

    /**
     * Specifies whether the redirection is temporary or not.
     *
     * @param temporary `false`, if the client should remember this redirection
     */
    public fun mode(temporary: Boolean): RedirectProviderBuilder {
        this.temporary = temporary
        return this
    }

    override fun add(concern: ConcernBuilder): RedirectProviderBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val location = location ?: throw BuilderMissingPropertyException("Location")

        // .NET accepts relative locations with characters java.net.URI rejects (e.g. "{sitemap}"),
        // so fall back to a URI with these characters quoted
        val parsed = try {
            URI(location)
        } catch (_: URISyntaxException) {
            try {
                URI(null, location, null)
            } catch (e: URISyntaxException) {
                throw IllegalStateException("The given location is not a valid URI", e)
            }
        }

        return Concerns.chain(concerns, RedirectProvider(parsed, temporary))
    }

    // endregion

}
