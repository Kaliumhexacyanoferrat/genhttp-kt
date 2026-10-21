package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.strings.StringContent

import kotlin.reflect.KClass

/**
 * Reads and writes enum values by their name (the names are matched case-sensitively).
 */
public class EnumFormatter : Formatter {

    override fun canHandle(type: KClass<*>): Boolean = type.java.isEnum

    override fun read(value: ByteString, type: KClass<*>): Any {
        val name = value.toString()

        for (constant in type.java.enumConstants) {
            if ((constant as Enum<*>).name == name) {
                return constant
            }
        }

        throw IllegalArgumentException("Invalid enum value '$name' for type ${type.simpleName}")
    }

    override fun write(value: Any, type: KClass<*>): String = (value as Enum<*>).name

    override fun getContent(value: Any): ResponseContent = StringContent((value as Enum<*>).name)

}
