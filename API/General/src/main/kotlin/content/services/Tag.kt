package org.genhttp.api.content.services

/**
 * Specifies the group the operations of a service are listed under when presented to the consumers of the API.
 *
 * If placed on a service class, all operations of the service will be grouped under the given name instead of
 * the name of the class. If placed on a function, the operation will be listed under the given name regardless
 * of the service it is declared in.
 *
 * @property name The name of the group the operations are listed under
 */
@Documentation
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Tag(val name: String)
