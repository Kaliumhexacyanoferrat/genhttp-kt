package org.genhttp.api.content.services

import org.genhttp.api.protocol.ResponseStatus

import kotlin.reflect.KClass

/**
 * Declares a header that is sent with the response of an operation.
 *
 * If no status is given, the header will be added to all successful responses (2xx) of the operation. Otherwise,
 * it will be added to the response with the given status, if the operation declares such a response
 * (see [ApiResponse]). Placed on a service class, the header applies to all operations of the service.
 *
 * @property name The name of the header (e.g. "Location")
 * @property description Describes the value sent with the header (empty, if not set)
 * @property status The status of the response the header is sent with (at most one value). If empty, the header
 * is sent with every successful response (`ResponseStatus? Status` in GenHTTP, as annotation parameters cannot be null)
 * @property type The type of the value of the header, if it is not a plain string (`Nothing::class`, if not set)
 */
@Documentation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
public annotation class ApiResponseHeader(
    val name: String,
    val description: String = "",
    val status: Array<ResponseStatus> = [],
    val type: KClass<*> = Nothing::class
)
