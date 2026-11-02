package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.documentation.ApiResponse
import org.genhttp.api.content.documentation.OperationDocumentation
import org.genhttp.api.content.documentation.Remarks
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.reflection.DocumentedInterceptor
import org.genhttp.modules.reflection.operations.Operation

import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

/**
 * Extends the description of a tool function with its remarks and the documentation of its interceptors, so the
 * model knows about the reasons a call might be refused (e.g. missing roles).
 */
internal object ToolDocumentation {

    fun describe(description: String, method: KFunction<*>, operation: Operation): String {
        val remarks = method.findAnnotation<Remarks>()?.text

        val errors = getErrors(operation)

        if (remarks == null && errors.isEmpty()) {
            return description
        }

        val result = StringBuilder(description)

        if (remarks != null) {
            result.append("\n\n").append(remarks)
        }

        if (errors.isNotEmpty()) {
            result.append("\n\nPossible errors:")

            for (error in errors) {
                result.append("\n- ").append(error)
            }
        }

        return result.toString()
    }

    private fun getErrors(operation: Operation): List<String> {
        val documentation = OperationDocumentation(RequestMethod.Post)

        for (interceptor in operation.interceptors) {
            if (interceptor is DocumentedInterceptor) {
                interceptor.addDocumentation(documentation)
            }
        }

        val errors = mutableListOf<String>()

        for (response in documentation.entries.filterIsInstance<ApiResponse>()) {
            if (response.status.code >= 400 && response.description.isNotBlank() && response.description !in errors) {
                errors.add(response.description)
            }
        }

        return errors
    }

}
