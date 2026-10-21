package org.genhttp.modules.conversion.formatters

import org.genhttp.api.infrastructure.Builder

import kotlin.reflect.full.createInstance

/**
 * Configures the formatters to be used to convert values from and to their string representation.
 */
public class FormatterBuilder : Builder<FormatterRegistry> {

    private val registry = mutableListOf<Formatter>()

    // region Functionality

    /**
     * Adds the given formatter to the registry.
     *
     * @param formatter The formatter to be added
     */
    public fun add(formatter: Formatter): FormatterBuilder {
        registry.add(formatter)
        return this
    }

    /**
     * Adds a new instance of the given formatter type to the registry. The formatter
     * needs to provide a constructor without parameters.
     *
     * @param T The type of the formatter to be added
     */
    public inline fun <reified T : Formatter> add(): FormatterBuilder = add(T::class.createInstance())

    /**
     * Builds the formatter registry based on the configuration.
     *
     * @return The newly created formatter registry
     */
    override fun build(): FormatterRegistry = FormatterRegistry(registry.toList())

    // endregion

}
