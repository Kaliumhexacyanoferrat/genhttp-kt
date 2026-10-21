package org.genhttp.api

/**
 * Marks the builders of genhttp-kt as a DSL, so that configuration lambdas such as
 * `Layout.create { add("api", api) }` do not accidentally call functions of an outer builder.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class GenHttpDsl
