package org.genhttp.modules.controllers

/**
 * Marks a parameter of a controller action to be read from the path (e.g. `/action/123/` for `action(@FromPath id: Int)`).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class FromPath
