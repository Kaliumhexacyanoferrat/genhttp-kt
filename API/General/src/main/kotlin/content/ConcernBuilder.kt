package org.genhttp.api.content

/**
 * Interface which needs to be implemented by factories providing concern instances.
 */
public interface ConcernBuilder {

    /**
     * Creates a new concern instance wrapping the given handler.
     *
     * @param content The handler the concern should be added to
     * @return The newly created concern
     */
    public fun build(content: Handler): Concern

}
