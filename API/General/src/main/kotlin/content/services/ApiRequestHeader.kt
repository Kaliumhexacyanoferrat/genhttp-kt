package org.genhttp.api.content.services

import kotlin.reflect.KClass

/**
 * Declares a header that is read from the request by an operation. Placed on a service class, the header applies
 * to all operations of the service.
 *
 * @property name The name of the header (e.g. "X-Request-Id")
 * @property description Describes the value expected to be sent by the client (empty, if not set)
 * @property required Whether the operation requires the client to send this header
 * @property type The type of the value of the header, if it is not a plain string (`Nothing::class`, if not set)
 */
@Documentation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
public annotation class ApiRequestHeader(
    val name: String,
    val description: String = "",
    val required: Boolean = false,
    val type: KClass<*> = Nothing::class
)
