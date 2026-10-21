package org.genhttp.modules.webservices

import org.genhttp.modules.reflection.Method
import org.genhttp.modules.reflection.MethodAttribute

/**
 * Marks a function of a webservice as an operation that can be invoked by clients.
 *
 * ```kotlin
 * @ResourceMethod
 * fun all(): List<Book> = ...
 *
 * @ResourceMethod(Method.Put, ":id")
 * fun update(id: Int, book: Book): Book = ...
 *
 * @ResourceMethod(path = ":id")
 * fun get(id: Int): Book? = ...
 * ```
 *
 * @property requestMethod The request method handled by the operation
 * @property path The path of the operation relative to the service, if any (e.g. ":id")
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class ResourceMethod(
    val requestMethod: Method = Method.Get,
    val path: String = ""
)

/**
 * The configuration of an operation declared via [ResourceMethod] (`ResourceMethodAttribute` in GenHTTP).
 *
 * @property path The path of the operation, if any
 */
public class ResourceMethodConfiguration(annotation: ResourceMethod) : MethodAttribute(annotation.requestMethod) {

    /**
     * The path of the operation relative to the service, if any.
     */
    public val path: String? = annotation.path.ifEmpty { null }

}
