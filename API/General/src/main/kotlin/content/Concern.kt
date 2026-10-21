package org.genhttp.api.content

/**
 * Functionality that wraps around a regular handler to add a concern such as response compression.
 */
public interface Concern : Handler {

    /**
     * The actual handler the concern is added to.
     */
    public val content: Handler

    /**
     * Anchor for factory functions provided by modules (e.g. `Concern.from { ... }`).
     */
    public companion object

}
