package org.genhttp.modules.dependencyinjection.infrastructure

import org.koin.core.scope.Scope

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Creates instances of types that are not registered with the container by resolving the
 * parameters of their constructor from a scope (counterpart of `ActivatorUtilities`).
 */
internal object Activator {

    /**
     * Creates an instance of the given type, resolving the arguments of its constructor from the scope.
     *
     * Optional parameters are omitted if they cannot be resolved, nullable ones are set to null.
     *
     * @throws IllegalStateException Thrown if the type cannot be constructed
     */
    fun <T : Any> create(type: KClass<T>, scope: Scope): T {
        val constructor = findConstructor(type)
            ?: throw IllegalStateException("A suitable constructor for type '${type.qualifiedName ?: type}' could not be located.")

        val arguments = HashMap<KParameter, Any?>()

        for (parameter in constructor.parameters) {
            val classifier = parameter.type.classifier as? KClass<*>

            val value = classifier?.let { scope.getOrNull<Any>(it) }

            if (value != null) {
                arguments[parameter] = value
            } else if (parameter.isOptional) {
                continue
            } else if (parameter.type.isMarkedNullable) {
                arguments[parameter] = null
            } else {
                throw IllegalStateException("Unable to resolve service for type '${parameter.type}' while attempting to activate '${type.qualifiedName ?: type}'.")
            }
        }

        constructor.isAccessible = true

        return constructor.callBy(arguments)
    }

    private fun <T : Any> findConstructor(type: KClass<T>): KFunction<T>? =
        type.primaryConstructor
            ?: type.constructors
                .filter { it.visibility == KVisibility.PUBLIC }
                .maxByOrNull { it.parameters.size }

}
