package org.genhttp.modules.reflection

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.modules.reflection.operations.Operation

import kotlin.reflect.KClass

/**
 * Declares an interceptor to be executed before a service method (`InterceptWithAttribute<T>` in GenHTTP).
 *
 * Can be placed on a service method directly or on an annotation class, so that every method annotated with that
 * annotation is intercepted (and the interceptor is configured with that annotation):
 *
 * ```kotlin
 * @InterceptWith(RoleInterceptor::class)
 * annotation class RequireRole(vararg val roles: String)
 * ```
 *
 * @property interceptor The type of interceptor to be instantiated (needs a parameterless constructor)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
public annotation class InterceptWith(val interceptor: KClass<out OperationInterceptor>)
