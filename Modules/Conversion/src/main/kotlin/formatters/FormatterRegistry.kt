package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent

import java.util.concurrent.ConcurrentHashMap

import kotlin.reflect.KClass

/**
 * Holds the formatters that can be used to convert values from and to their string representation.
 * The first formatter that is able to handle a type will be used.
 *
 * @param formatters The formatters to be used, in the order of their priority
 */
public class FormatterRegistry(public val formatters: List<Formatter>) {

    // the formatter selected for a type (or NoFormatter), so that the formatters are not asked on every conversion
    private val selected = ConcurrentHashMap<Class<*>, Any>()

    /**
     * Checks whether any of the formatters can handle the given type.
     *
     * @param type The type to be checked
     * @return true, if the type can be read and written
     */
    public fun canHandle(type: KClass<*>): Boolean = select(type) != null

    /**
     * Converts the given memory value into the specified type.
     *
     * @param value The value to be converted
     * @param type The type to convert the value to
     * @return The converted value, or null if there is no formatter that can handle the type
     */
    public fun read(value: ByteString, type: KClass<*>): Any? = select(type)?.read(value, type)

    /**
     * Converts the given value into its string representation.
     *
     * @param value The value to be formatted
     * @param type The declared type of the value
     * @return The string representation, or null if there is no formatter that can handle the type
     */
    public fun write(value: Any, type: KClass<*>): String? = select(type)?.write(value, type)

    /**
     * Creates response content from the given value.
     *
     * @param value The value to be sent to the client
     * @param type The declared type of the value
     * @return The content to be set on the response, or null if there is no formatter that can handle the type
     */
    public fun getContent(value: Any, type: KClass<*>): ResponseContent? = select(type)?.getContent(value)

    private fun select(type: KClass<*>): Formatter? {
        val key = type.java

        val known = selected[key]

        if (known != null) {
            return known as? Formatter
        }

        val formatter = formatters.firstOrNull { it.canHandle(type) }

        selected[key] = formatter ?: NoFormatter

        return formatter
    }

    private object NoFormatter

}
