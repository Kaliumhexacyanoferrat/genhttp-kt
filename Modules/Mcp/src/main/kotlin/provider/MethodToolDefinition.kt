package org.genhttp.modules.mcp.provider

import org.genhttp.api.protocol.Request
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.hints
import org.genhttp.modules.mcp.schema.SchemaGenerator
import org.genhttp.modules.mcp.toolName
import org.genhttp.modules.mcp.toolTitle

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

/**
 * A tool implemented by a function or a lambda, before it has been analyzed against the server it will run on.
 *
 * @property method The function implementing the tool (for lambdas, the function describing their signature)
 * @property delegate The lambda implementing the tool, if it is not implemented by a function
 * @property instance Provides the instance to invoke the function on (null for functions without receiver)
 * @property activatedType The type to create an instance of, if not provided yet
 */
internal data class MethodToolDefinition(
    val name: String,
    val title: String?,
    val description: String,
    val hints: ToolHints?,
    val method: KFunction<*>,
    val delegate: Function<*>?,
    val instance: (suspend (Request) -> Any)?,
    val activatedType: KClass<*>? = null
) {

    companion object {

        /**
         * The annotations marking webservice or controller methods (`MethodAttribute` upstream, which cannot be
         * referenced here as the frameworks are not dependencies of this module).
         */
        private val MethodAnnotations = setOf(
            "org.genhttp.modules.webservices.ResourceMethod",
            "org.genhttp.modules.controllers.ControllerAction"
        )

        fun discover(type: KClass<*>, instance: (suspend (Request) -> Any)?): List<MethodToolDefinition> {
            val members = publicFunctions(type)

            // functions of the companion object are the counterpart of static methods
            val companion = type.companionObject
            val companionInstance = type.companionObjectInstance

            val statics = if (companion != null && companionInstance != null) publicFunctions(companion) else emptyList()

            if ((members + statics).any { method -> method.annotations.any { it.annotationClass.qualifiedName in MethodAnnotations } }) {
                throw IllegalStateException("Type '${type.displayName}' provides webservice or controller methods and cannot be used to provide tools. Please declare your tools in a dedicated class.")
            }

            val result = mutableListOf<MethodToolDefinition>()

            for (method in members + statics) {
                val attribute = method.findAnnotation<Tool>() ?: continue

                val name = attribute.toolName ?: ToolNames.fromMethod(method.name)

                ToolNames.validate(name)

                val description = SchemaGenerator.findDescription(method.annotations)
                    ?: throw IllegalStateException("Tool '$name' (${type.displayName}.${method.name}) needs to be described for the model via a @Summary or @JsonPropertyDescription annotation.")

                val static = method in statics

                val provider: (suspend (Request) -> Any)? = when {
                    static -> { _ -> companionInstance!! }
                    else -> instance
                }

                val activated = if (!static && instance == null) type else null

                result.add(MethodToolDefinition(name, attribute.toolTitle, description, attribute.hints, method, null, provider, activated))
            }

            if (result.isEmpty()) {
                throw IllegalStateException("Type '${type.displayName}' does not declare any functions annotated with @Tool.")
            }

            return result
        }

        /**
         * The public member functions of the type, including the inherited ones (`GetMethods(BindingFlags.Public)`).
         */
        private fun publicFunctions(type: KClass<*>): List<KFunction<*>> =
            type.memberFunctions.filter { it.visibility == KVisibility.PUBLIC }

        @OptIn(ExperimentalReflectionOnLambdas::class)
        fun fromDelegate(name: String, description: String, function: Function<*>): MethodToolDefinition {
            ToolNames.validate(name)

            val method: KFunction<*>
            val delegate: Function<*>?

            if (function is KFunction<*>) {
                if (function.parameters.any { it.kind == KParameter.Kind.INSTANCE || it.kind == KParameter.Kind.EXTENSION_RECEIVER }) {
                    throw IllegalArgumentException("Function '${function.name}' requires an instance, use a bound reference (instance::${function.name}) instead")
                }

                method = function
                delegate = null
            } else {
                method = function.reflect()
                    ?: throw IllegalArgumentException(
                        "Unable to read the signature of the given lambda. Use a suspend lambda with explicitly typed " +
                                "parameters (e.g. `add(\"add\", \"Adds\") { a: Int, b: Int -> a + b }`) or a function reference (e.g. `add(\"add\", \"Adds\", ::add)`)."
                    )

                delegate = function
            }

            val attribute = method.findAnnotation<Tool>()

            if (attribute?.toolName != null) {
                throw IllegalStateException("The name of function tool '$name' is passed when adding it and must not be specified via the @Tool annotation.")
            }

            return MethodToolDefinition(name, attribute?.toolTitle, description, attribute?.hints, method, delegate, null)
        }

        private val KClass<*>.displayName: String
            get() = qualifiedName ?: java.name

    }

}
