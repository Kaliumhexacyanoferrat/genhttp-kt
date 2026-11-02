package org.genhttp.modules.mcp.schema

import tools.jackson.databind.json.JsonMapper

import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * Checks the arguments sent by a client against the constraints that are also advertised in the schema of the
 * tool.
 *
 * Validators are only created for types that declare constraints, so arguments without any constraints are not
 * inspected at all.
 */
internal interface ArgumentValidator {

    fun validate(value: Any?, path: String, errors: MutableList<String>)

}

internal object ArgumentValidation {

    fun forParameter(parameter: KParameter, mapper: JsonMapper): ArgumentValidator? =
        combine(Constraints.find(parameter), forType(parameter.type, mapper, HashMap()))

    fun format(errors: List<String>): String = "The arguments are invalid:\n- ${errors.joinToString("\n- ")}"

    private fun forType(type: KType, mapper: JsonMapper, pending: MutableMap<String, PendingValidator>): ArgumentValidator? {
        val key = McpSerialization.keyOf(type)

        pending[key]?.let { return it }

        val typeInfo = McpSerialization.getTypeInfo(mapper, type)

        val elementType = typeInfo.elementType

        if (typeInfo.kind == TypeKind.Enumerable && elementType != null) {
            val element = forType(elementType, mapper, pending)

            return if (element != null) CollectionValidator(element) else null
        }

        if (typeInfo.kind != TypeKind.Object) {
            return null
        }

        val placeholder = PendingValidator()

        pending[key] = placeholder

        val properties = mutableListOf<PropertyValidator>()

        for (property in typeInfo.properties) {
            val constraints = Constraints.find(property.annotations, property.parameterAnnotations)

            val validator = combine(constraints, forType(property.type, mapper, pending))

            if (validator != null) {
                properties.add(PropertyValidator(property.name, property::get, validator))
            }
        }

        placeholder.target = if (properties.isNotEmpty()) ObjectValidator(properties) else null

        return placeholder.target
    }

    private fun combine(constraints: List<Annotation>, inner: ArgumentValidator?): ArgumentValidator? =
        if (constraints.isNotEmpty()) ConstraintValidator(constraints, inner) else inner

    private fun join(path: String, name: String): String = if (path.isNotEmpty()) "$path.$name" else name

    private class ConstraintValidator(private val constraints: List<Annotation>, private val inner: ArgumentValidator?) : ArgumentValidator {

        override fun validate(value: Any?, path: String, errors: MutableList<String>) {
            for (constraint in constraints) {
                if (!Constraints.isValid(constraint, value)) {
                    errors.add("$path: ${Constraints.message(constraint)}")
                }
            }

            inner?.validate(value, path, errors)
        }

    }

    private class ObjectValidator(private val properties: List<PropertyValidator>) : ArgumentValidator {

        override fun validate(value: Any?, path: String, errors: MutableList<String>) {
            if (value == null) {
                return
            }

            for (property in properties) {
                property.validator.validate(property.getter(value), join(path, property.name), errors)
            }
        }

    }

    private class CollectionValidator(private val element: ArgumentValidator) : ArgumentValidator {

        override fun validate(value: Any?, path: String, errors: MutableList<String>) {
            val items: Iterator<Any?> = when (value) {
                is Iterable<*> -> value.iterator()
                is Array<*> -> value.iterator()
                else -> return
            }

            var index = 0

            for (item in items) {
                element.validate(item, "$path[${index++}]", errors)
            }
        }

    }

    private class PendingValidator : ArgumentValidator {

        var target: ArgumentValidator? = null

        override fun validate(value: Any?, path: String, errors: MutableList<String>) {
            target?.validate(value, path, errors)
        }

    }

    private class PropertyValidator(val name: String, val getter: (Any) -> Any?, val validator: ArgumentValidator)

}
