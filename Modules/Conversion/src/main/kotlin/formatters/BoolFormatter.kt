package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.strings.StringContent

import kotlin.reflect.KClass

/**
 * Reads and writes booleans, which are represented by the values "1", "on" and "true"
 * as well as "0", "off" and "false" (ignoring case). Booleans are written as "1" and "0".
 */
public class BoolFormatter : Formatter {

    private companion object {

        val SwitchOne = ByteString("1")
        val SwitchZero = ByteString("0")

        val SwitchOn = ByteString("on")
        val SwitchOff = ByteString("off")

        val SwitchTrue = ByteString("true")
        val SwitchFalse = ByteString("false")

        val TrueContent = StringContent("1")
        val FalseContent = StringContent("0")

    }

    override fun canHandle(type: KClass<*>): Boolean = type == Boolean::class

    override fun read(value: ByteString, type: KClass<*>): Any {
        if (value == SwitchOne || value == SwitchOn || value == SwitchTrue) {
            return true
        }

        if (value == SwitchZero || value == SwitchOff || value == SwitchFalse) {
            return false
        }

        throw IllegalArgumentException("Cannot convert given value to bool")
    }

    override fun write(value: Any, type: KClass<*>): String = if (value as Boolean) "1" else "0"

    override fun getContent(value: Any): ResponseContent {
        if (value is Boolean) {
            return if (value) TrueContent else FalseContent
        }

        throw UnsupportedOperationException()
    }

}
