package org.genhttp.api.content.services

/**
 * Briefly describes an element of a service, so it can be presented to the consumers of the API
 * (e.g. in a generated OpenAPI specification).
 *
 * On a function, the text describes the operation. On a parameter, it describes the argument expected by the
 * operation and on the return type (`fun get(): @Summary("...") Item`) it describes the successful response.
 * On a service class, it describes the group the operations of the service are listed under.
 *
 * @property text The text describing the annotated element
 */
@Documentation
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
    AnnotationTarget.PROPERTY, AnnotationTarget.FIELD
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Summary(val text: String)
