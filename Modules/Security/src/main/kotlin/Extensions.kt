@file:JvmName("Extensions")

package org.genhttp.modules.security

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.modules.security.providers.SecureUpgradeConcernBuilder
import org.genhttp.modules.security.providers.SnifferPreventionConcernBuilder
import org.genhttp.modules.security.providers.StrictTransportConcernBuilder
import org.genhttp.modules.security.providers.StrictTransportPolicy

import kotlin.time.Duration.Companion.days

/**
 * Applies common hardening measures to the server.
 *
 * @param secureUpgrade Whether insecure requests should be redirected to secured endpoints
 * @param strictTransport Whether clients should be instructed to only use secured connections (HSTS)
 * @param preventSniffing Whether browsers should be prevented from guessing content types
 * @return The host the concerns have been added to
 */
public fun ServerHost.harden(
    secureUpgrade: Boolean = true,
    strictTransport: Boolean = true,
    preventSniffing: Boolean = true
): ServerHost {
    if (secureUpgrade) {
        this.secureUpgrade(SecureUpgrade.Force)
    }

    if (strictTransport) {
        this.strictTransport(StrictTransportPolicy(365.days, true, true))
    }

    if (preventSniffing) {
        this.preventSniffing()
    }

    return this
}

/**
 * Adds a concern to the host that redirects insecure requests to a secured endpoint.
 *
 * @param mode The strategy to be used to upgrade requests (no concern is added for [SecureUpgrade.None])
 * @return The host the concern has been added to
 */
public fun ServerHost.secureUpgrade(mode: SecureUpgrade): ServerHost {
    if (mode != SecureUpgrade.None) {
        add(SecureUpgradeConcernBuilder().mode(mode))
    }

    return this
}

/**
 * Adds a concern to the host that instructs clients to only use secured connections (HSTS).
 *
 * @param policy The policy to be announced to the clients
 * @return The host the concern has been added to
 */
public fun ServerHost.strictTransport(policy: StrictTransportPolicy): ServerHost {
    add(StrictTransportConcernBuilder().policy(policy))
    return this
}

/**
 * Adds a concern to the host that prevents browsers from guessing the content type of responses.
 *
 * @return The host the concern has been added to
 */
public fun ServerHost.preventSniffing(): ServerHost {
    add(SnifferPreventionConcernBuilder())
    return this
}
