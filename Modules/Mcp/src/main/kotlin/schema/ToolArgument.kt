package org.genhttp.modules.mcp.schema

import tools.jackson.databind.JavaType

import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * A parameter of a tool function that is read from the arguments sent by the client.
 *
 * @property required Whether the client needs to send the argument (parameters without default value that are not nullable)
 * @property javaType The type to deserialize the argument with
 * @property validator Checks the constraints declared for the argument, if any
 */
internal class ToolArgument(
    val name: String,
    val parameter: KParameter,
    val required: Boolean,
    val javaType: JavaType,
    val validator: ArgumentValidator?
) {

    val type: KType
        get() = parameter.type

}
