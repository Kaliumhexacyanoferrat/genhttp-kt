package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.modules.security.SecureUpgrade

/**
 * Builds a concern that redirects requests to insecure endpoints to the secured endpoints of the server.
 */
public class SecureUpgradeConcernBuilder : ConcernBuilder {

    private var mode: SecureUpgrade = SecureUpgrade.Force

    // region Functionality

    /**
     * Sets the strategy to be used to upgrade requests (defaults to [SecureUpgrade.Force]).
     *
     * @param mode The strategy to be used
     */
    public fun mode(mode: SecureUpgrade): SecureUpgradeConcernBuilder {
        this.mode = mode
        return this
    }

    override fun build(content: Handler): Concern = SecureUpgradeConcern(content, mode)

    // endregion

}
