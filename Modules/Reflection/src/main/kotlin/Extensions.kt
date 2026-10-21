@file:JvmName("Extensions")

package org.genhttp.modules.reflection

import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.formatters.FormatterRegistry

import kotlin.reflect.KClass
import kotlin.reflect.KParameter

/**
 * Checks whether the value of the given parameter can be converted from a simple string (e.g. a query parameter).
 */
public fun KParameter.canFormat(formatters: FormatterRegistry): Boolean {
    val classifier = type.classifier as? KClass<*> ?: return false
    return formatters.canHandle(classifier)
}

/**
 * Checks whether the given parameter accepts null values.
 */
public fun KParameter.checkNullable(): Boolean = type.isMarkedNullable

/**
 * Converts the given variable name into a named regular expression group matching a path segment.
 */
public fun String.toParameter(): String = "(?<$this>[^/]+)"

/**
 * Applies the given adjustments to the response builder, if any.
 */
internal fun ResponseBuilder.adjust(adjustments: (ResponseBuilder.() -> Unit)?): ResponseBuilder {
    adjustments?.invoke(this)
    return this
}
