package org.genhttp.modules.redirects

import org.genhttp.modules.redirects.provider.RedirectProviderBuilder

/**
 * Entry point to create handlers that redirect the requesting client to another location.
 */
public object Redirect {

    /**
     * Redirects the requesting client to the specified location.
     *
     * ```kotlin
     * Redirect.to("https://genhttp.org", true)
     * Redirect.to("some/other/site", false)
     * Redirect.to("{sitemap}", true)
     * ```
     *
     * @param location The location to redirect the client to (an absolute or relative route)
     * @param temporary `false`, if the client should remember this redirection
     */
    public fun to(location: String, temporary: Boolean = false): RedirectProviderBuilder =
        RedirectProviderBuilder().location(location).mode(temporary)

}
