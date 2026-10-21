package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent

import kotlin.reflect.KClass

/**
 * Allows to add support for a specific type to be used as a parameter
 * of a web service or controller as well as in form encoded data.
 *
 * The generic `Read<T>` of GenHTTP is available as the reified extension function `read<T>(value)`.
 */
public interface Formatter {

    /**
     * Checks, whether the formatter is capable of handling the given type.
     *
     * @param type The type to be checked
     * @return true, if the formatter can be used to read and write such types
     */
    public fun canHandle(type: KClass<*>): Boolean

    /**
     * Converts the given memory value into the specified type.
     *
     * Used by the framework to read parameters to be passed to controllers or the like.
     *
     * @param value The value to be converted
     * @param type The type to convert the value to
     * @return The value converted into the given type
     */
    public fun read(value: ByteString, type: KClass<*>): Any?

    /**
     * Converts the given object instance of the given type into a string representation.
     *
     * Used by the framework to serialize a single value into the response's body
     * or to generate form encoded data.
     *
     * @param value The value to be formatted
     * @param type The declared type of the value
     * @return The string representation of the given value
     */
    public fun write(value: Any, type: KClass<*>): String?

    /**
     * Creates response content from the given value.
     *
     * @param value The value to be sent to the client
     * @return The content to be set on the response
     */
    public fun getContent(value: Any): ResponseContent

}
