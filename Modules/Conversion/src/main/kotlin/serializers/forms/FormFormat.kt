package org.genhttp.modules.conversion.serializers.forms

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.convertTo
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.ByteStreamSerialization
import org.genhttp.modules.conversion.serializers.SerializationFormat

import java.io.InputStream
import java.net.URLDecoder
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Reads and writes objects as form encoded data (`application/x-www-form-urlencoded`).
 *
 * Objects are deserialized by creating an instance via a public constructor without parameters
 * and then setting its mutable properties (or public fields) by name, ignoring the case of the name.
 * As an addition to GenHTTP, Kotlin classes whose primary constructor only has optional parameters
 * (such as data classes with default values) are supported as well: the constructor parameters are
 * passed by name, parameters without a matching form field keep their default value.
 *
 * @param formatters The formatters used to convert the values of the fields
 */
public class FormFormat(private val formatters: FormatterRegistry) : SerializationFormat {

    /**
     * Creates a format that uses the default formatters.
     */
    public constructor() : this(Formatting.default().build())

    // region Functionality

    override suspend fun deserialize(stream: InputStream, type: KType): Any? {
        val content = stream.reader(Charsets.UTF_8).use { it.readText() }

        val query = parseQuery(content)

        val kClass = type.classifier as? KClass<*>
            ?: throw ProviderException(ResponseStatus.InternalServerError, "Instance of type '$type' cannot be constructed")

        val parameterless = kClass.constructors.firstOrNull { it.parameters.isEmpty() }

        val constructor = parameterless ?: kClass.primaryConstructor?.takeIf { c -> c.parameters.all { it.isOptional } }

        if (constructor == null) {
            throw ProviderException(
                ResponseStatus.InternalServerError,
                "Instance of type '$type' cannot be constructed as there is no parameterless constructor"
            )
        }

        constructor.isAccessible = true

        // properties which are not set via the constructor
        val remaining = LinkedHashMap(query)

        val arguments = LinkedHashMap<KParameter, Any?>()

        for (parameter in constructor.parameters) {
            val key = remaining.keys.firstOrNull { it.equals(parameter.name, ignoreCase = true) }

            if (key != null) {
                arguments[parameter] = convert(remaining.remove(key)!!, parameter.type)
            }
        }

        val result = constructor.callBy(arguments)

        for ((key, value) in remaining) {
            val property = kClass.memberProperties.firstOrNull {
                it.name.equals(key, ignoreCase = true) && it.visibility == KVisibility.PUBLIC
            }

            if (property != null) {
                // read-only properties cannot be set (they are ignored by GenHTTP as well)
                if (property is KMutableProperty1<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    property as KMutableProperty1<Any, Any?>

                    property.isAccessible = true
                    property.set(result, convert(value, property.returnType))
                }
            } else {
                val field = kClass.java.fields.firstOrNull {
                    it.name.equals(key, ignoreCase = true) && !Modifier.isStatic(it.modifiers)
                }

                if (field != null && !Modifier.isFinal(field.modifiers)) {
                    field.set(result, convert(value, field.type.kotlin.createType(nullable = !field.type.isPrimitive)))
                }
            }
        }

        return result
    }

    override suspend fun serialize(request: Request, response: Any): ResponseBuilder =
        request.respond().content(FormContent(response::class, response, formatters))

    override suspend fun serialize(data: Any): ByteBuffer = ByteStreamSerialization.serialize { buffer ->
        FormContent(data::class, data, formatters).write(buffer)
    }

    // endregion

    // region Helpers

    // very inefficient, but the form format is probably not used that much so we do not optimize that for now
    private fun convert(value: String, type: KType): Any? = ByteString(value.toByteArray(Charsets.UTF_8)).convertTo(type, formatters)

    private fun parseQuery(content: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        for (pair in content.removePrefix("?").split('&')) {
            if (pair.isEmpty()) {
                continue
            }

            val index = pair.indexOf('=')

            // pairs without a name are ignored
            if (index < 0) {
                continue
            }

            val key = decode(pair.substring(0, index))
            val value = decode(pair.substring(index + 1))

            // values of repeated fields are combined
            result.merge(key, value) { old, new -> "$old,$new" }
        }

        return result
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        value
    }

    // endregion

}
