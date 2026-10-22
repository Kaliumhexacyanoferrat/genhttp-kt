package org.genhttp.modules.reflection

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Modifier

import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

/**
 * Invokes a member function through a method handle (used by [ExecutionMode.Auto], the counterpart of the code
 * GenHTTP generates for an operation).
 *
 * kotlin-reflect validates and copies the arguments on every call, which costs more than reading the arguments
 * of a simple operation. The handle is resolved once and called with the positional arguments directly.
 *
 * @property arity The number of arguments to be passed (the value parameters, plus the continuation of
 * suspend functions, which is set by the invoker)
 */
internal class MethodInvoker private constructor(
    private val handle: MethodHandle,
    private val suspending: Boolean,
    private val returnsUnit: Boolean,
    val arity: Int
) {

    /**
     * Invokes the function on the given instance.
     *
     * @param arguments The values of the value parameters, with one additional slot for the continuation of a
     * suspend function (see [arity])
     */
    suspend fun invoke(instance: Any, arguments: Array<Any?>): Any? {
        val result = if (suspending) {
            suspendCoroutineUninterceptedOrReturn { continuation ->
                arguments[arguments.size - 1] = continuation
                handle.invokeExact(instance, arguments)
            }
        } else {
            handle.invokeExact(instance, arguments)
        }

        // like KCallable.call(), functions returning Unit result in Unit
        return if (returnsUnit) Unit else result
    }

    companion object {

        /**
         * Creates an invoker for the given member function, if the function can be invoked without kotlin-reflect
         * (value classes, for example, require kotlin-reflect to box and unbox values).
         */
        fun create(function: KFunction<*>): MethodInvoker? = try {
            createInvoker(function)
        } catch (_: Exception) {
            null
        }

        private fun createInvoker(function: KFunction<*>): MethodInvoker? {
            val parameters = function.parameters

            if (parameters.isEmpty() || parameters[0].kind != KParameter.Kind.INSTANCE || parameters.drop(1).any { it.kind != KParameter.Kind.VALUE }) {
                return null
            }

            if (parameters.any { it.type.isValueClass() } || function.returnType.isValueClass()) {
                return null
            }

            val method = function.javaMethod ?: return null

            if (Modifier.isStatic(method.modifiers)) {
                return null
            }

            val suspending = function.isSuspend
            val arity = parameters.size - 1 + (if (suspending) 1 else 0)

            if (method.parameterCount != arity) {
                return null
            }

            method.isAccessible = true

            val handle = MethodHandles.lookup().unreflect(method)
                .asSpreader(Array<Any?>::class.java, arity)
                .asType(MethodType.methodType(Any::class.java, Any::class.java, Array<Any?>::class.java))

            val returnsUnit = if (suspending) {
                function.returnType.classifier == Unit::class && !function.returnType.isMarkedNullable
            } else {
                method.returnType == Void.TYPE
            }

            return MethodInvoker(handle, suspending, returnsUnit, arity)
        }

        private fun kotlin.reflect.KType.isValueClass(): Boolean = (classifier as? KClass<*>)?.isValue == true

    }

}
