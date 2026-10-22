package org.genhttp.modules.conversion.serializers.forms

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.conversion.formatters.FormatterRegistry

import java.io.OutputStream
import java.nio.ByteBuffer

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Response content that writes the public properties of an object as form encoded data
 * (`application/x-www-form-urlencoded`).
 *
 * @param type The type of the object to be written
 * @param data The object to be written
 * @param formatters The formatters used to convert the values of the properties
 */
public class FormContent(
    type: KClass<*>,
    private val data: Any,
    private val formatters: FormatterRegistry
) : ResponseContent {

    private val dataType: KClass<*> = type

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = ContentType.ApplicationWwwFormUrlEncoded

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = data.hashCode().toLong()

    override suspend fun write(sink: ResponseSink) {
        write(sink.stream)
    }

    /**
     * Writes the form encoded representation of the object to the given stream.
     *
     * @param target The stream to write to (it is not closed)
     */
    public suspend fun write(target: OutputStream) {
        val values = LinkedHashMap<String, String>()

        for (property in properties(dataType)) {
            setValue(values, property.name, property.get(data), property.returnType.classifier)
        }

        val result = values.entries.joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }

        target.write(result.toByteArray(Charsets.UTF_8))
    }

    private fun setValue(values: MutableMap<String, String>, field: String, value: Any?, type: Any?) {
        if (value != null && type is KClass<*>) {
            val formatted = formatters.write(value, type)

            if (formatted != null) {
                values[field] = formatted
            }
        }
    }

    // endregion

    internal companion object {

        /**
         * The public properties (and public fields) of the given type in the order of their declaration.
         */
        fun properties(type: KClass<*>): List<KProperty1<Any, *>> {
            val order = mutableListOf<String>()

            var current: Class<*>? = type.java

            while (current != null && current != Any::class.java) {
                current.declaredFields.forEach { order.add(it.name) }
                current = current.superclass
            }

            @Suppress("UNCHECKED_CAST")
            val properties = type.memberProperties.filter { it.visibility == KVisibility.PUBLIC } as List<KProperty1<Any, *>>

            properties.forEach {
                try {
                    it.isAccessible = true
                } catch (_: Exception) {
                    // the property can still be used if the class is accessible
                }
            }

            return properties.sortedBy { order.indexOf(it.name).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        }

        /**
         * Encodes the value like `HttpUtility.UrlEncode` does, but encoding spaces as `%20` and
         * leaving plus signs untouched (as GenHTTP does).
         */
        fun urlEncode(value: String): String {
            val result = StringBuilder(value.length + 8)

            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val code = byte.toInt() and 0xFF
                val char = code.toChar()

                when {
                    char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> result.append(char)
                    char == '-' || char == '_' || char == '.' || char == '!' || char == '*' || char == '(' || char == ')' -> result.append(char)
                    char == ' ' -> result.append("%20")
                    char == '+' -> result.append('+')
                    else -> result.append('%').append(HexDigits[code shr 4]).append(HexDigits[code and 0xF])
                }
            }

            return result.toString()
        }

        private const val HexDigits = "0123456789abcdef"

    }

}
