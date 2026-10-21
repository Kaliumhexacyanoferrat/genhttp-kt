package org.genhttp.api.content.services

/**
 * Adds a detailed explanation to an element of a service that goes beyond the short text provided by the
 * [Summary] annotation. Depending on the consumer, the text may be rendered as Markdown (as for descriptions
 * within an OpenAPI specification).
 *
 * @property text The detailed explanation of the annotated element
 */
@Documentation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Remarks(val text: String)
