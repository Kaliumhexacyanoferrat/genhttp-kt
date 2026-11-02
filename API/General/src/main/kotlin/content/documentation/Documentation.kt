package org.genhttp.api.content.documentation

/**
 * Marks the annotations that document an API, so it can be presented to its consumers (e.g. in a generated
 * OpenAPI specification). This replaces the `DocumentationAttribute` base class of GenHTTP, as annotations
 * cannot inherit from each other in Kotlin.
 *
 * Concerns document the functionality they add to the content they wrap via [OperationDocumentation], which
 * creates instances of these annotations at runtime (see [DocumentedConcern]).
 *
 * The documentation annotations are [Summary], [Remarks], [Tag], [ApiRequestBody], [ApiRequestHeader], [ApiResponse]
 * and [ApiResponseHeader] (`RequestBodyAttribute`, `RequestHeaderAttribute`, `ResponseAttribute` and
 * `ResponseHeaderAttribute` in GenHTTP, prefixed to avoid clashes with the protocol types of the same name).
 *
 * Optional annotation parameters cannot be `null` in Kotlin. Empty strings and `Nothing::class` represent
 * values that have not been set.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Documentation

/**
 * Checks whether this annotation documents an API (see [Documentation]).
 */
public val Annotation.isDocumentation: Boolean
    get() = annotationClass.java.isAnnotationPresent(Documentation::class.java)
