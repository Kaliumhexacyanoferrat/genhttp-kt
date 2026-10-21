package org.genhttp.modules.security.providers

import kotlin.time.Duration

/**
 * Specifies how clients should be instructed to only use secured connections (HSTS).
 *
 * @param maximumAge The duration the client should remember that the server is only accessible via HTTPS
 * @param includeSubdomains Whether the policy applies to all subdomains as well
 * @param preload Whether the host may be included in the preload lists of browsers
 */
public class StrictTransportPolicy(
    maximumAge: Duration,
    includeSubdomains: Boolean,
    preload: Boolean
) {

    // region Get-/Setters

    /**
     * The duration the client should remember that the server is only accessible via HTTPS.
     */
    public val maximumAge: Duration = maximumAge

    /**
     * Whether the policy applies to all subdomains as well.
     */
    public val includeSubdomains: Boolean = includeSubdomains

    /**
     * Whether the host may be included in the preload lists of browsers.
     */
    public val preload: Boolean = preload

    // endregion

}
