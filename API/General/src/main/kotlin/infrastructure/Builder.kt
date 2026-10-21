package org.genhttp.api.infrastructure

/**
 * General interface implemented by every builder responsible to configure and setup an instance.
 *
 * Builders must not change their internal state when building an object, allowing builder instances to be
 * re-used if required.
 *
 * @param T The type which should be produced by the builder
 */
public interface Builder<out T> {

    /**
     * Creates a new instance of the specified type using the current configuration.
     *
     * @return The newly created instance
     */
    public fun build(): T

}
