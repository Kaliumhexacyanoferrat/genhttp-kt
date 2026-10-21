package org.genhttp.api.content.services

import kotlin.reflect.KClass

/**
 * Describes the body expected by an operation of a service.
 *
 * The request body is usually derived from the signature of the function implementing the operation. This
 * annotation allows to describe the body if the function reads it on its own (e.g. from the request or a stream),
 * or to restrict the content types that are accepted. Declaring multiple bodies with different content types
 * will merge them.
 *
 * @property description Describes the content to be sent by the client (empty, if not set)
 * @property type The type of the content expected by the operation, if it cannot be derived from the function
 * signature (`Nothing::class`, if not set)
 * @property contentType The content type expected by the operation (e.g. "application/zip"), if it is not
 * determined by the serialization formats supported by the service (empty, if not set)
 */
@Documentation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
public annotation class ApiRequestBody(
    val description: String = "",
    val type: KClass<*> = Nothing::class,
    val contentType: String = ""
)
