package org.genhttp.modules.conversion.serializers.json

import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.ResponseStatus

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule

/**
 * Serializes enums of the API with explicit numeric values as these values (like System.Text.Json writes enums
 * as their underlying value), e.g. `ResponseStatus.NotFound` as `404`. Names are accepted when reading as well.
 */
internal object ApiTypesModule : SimpleModule("genhttp-api-types") {

    private fun readResolve(): Any = ApiTypesModule

    init {
        addSerializer(ResponseStatus::class.java, NumericSerializer { it.code })
        addDeserializer(ResponseStatus::class.java, NumericDeserializer(ResponseStatus::class.java) { code -> ResponseStatus.fromCode(code) })

        addSerializer(Priority::class.java, NumericSerializer { it.value })
        addDeserializer(Priority::class.java, NumericDeserializer(Priority::class.java) { value -> Priority.entries.firstOrNull { it.value == value } })
    }

    private class NumericSerializer<T : Any>(private val value: (T) -> Int) : ValueSerializer<T>() {

        override fun serialize(item: T, generator: JsonGenerator, context: SerializationContext) {
            generator.writeNumber(value(item))
        }

    }

    private class NumericDeserializer<T : Enum<T>>(private val type: Class<T>, private val lookup: (Int) -> T?) : ValueDeserializer<T>() {

        override fun deserialize(parser: JsonParser, context: DeserializationContext): T? {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                val number = parser.intValue
                return lookup(number) ?: type.cast(context.handleWeirdNumberValue(type, number, "Unknown value"))
            }

            val text = parser.valueAsString

            return type.enumConstants.firstOrNull { it.name.equals(text, ignoreCase = true) }
                ?: type.cast(context.handleWeirdStringValue(type, text, "Unknown value"))
        }

    }

}
