@file:JvmName("FormatterExtensions")

package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent

/**
 * Converts the given memory value into the specified type.
 *
 * This is the reified equivalent of `IFormatter.Read<T>(value)`.
 *
 * @param T The type to convert the value to
 * @param value The value to be converted
 * @return The value converted into the given type
 */
public inline fun <reified T> Formatter.read(value: ByteString): T = read(value, T::class) as T

/**
 * Converts the given memory value into the specified type using the first formatter that can handle it.
 *
 * This is the reified equivalent of `FormatterRegistry.Read<T>(value)`.
 *
 * @param T The type to convert the value to
 * @param value The value to be converted
 * @return The value converted into the given type, or null if there is no formatter that can handle the type
 */
public inline fun <reified T> FormatterRegistry.read(value: ByteString): T? = read(value, T::class) as T?

/**
 * Creates response content from the given value using the first formatter that can handle its type.
 *
 * This is the reified equivalent of `FormatterRegistry.GetContent<T>(value)`.
 *
 * @param T The type of the value
 * @param value The value to be sent to the client
 * @return The content to be set on the response, or null if there is no formatter that can handle the type
 */
public inline fun <reified T : Any> FormatterRegistry.getContent(value: T): ResponseContent? = getContent(value, T::class)
