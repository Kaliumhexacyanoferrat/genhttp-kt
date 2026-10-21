package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.strings.StringContent

import kotlin.reflect.KClass

/**
 * Reads and writes strings (UTF-8 encoded).
 */
public class StringFormatter : Formatter {

    override fun canHandle(type: KClass<*>): Boolean = type == String::class

    override fun read(value: ByteString, type: KClass<*>): Any = Charsets.UTF_8.decode(value.bytes).toString()

    override fun write(value: Any, type: KClass<*>): String = value as String

    override fun getContent(value: Any): ResponseContent {
        if (value is String) {
            return StringContent(value)
        }

        throw UnsupportedOperationException()
    }

}
