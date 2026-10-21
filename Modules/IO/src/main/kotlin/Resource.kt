@file:JvmName("Resource")

package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.modules.io.classpath.ClasspathResourceBuilder
import org.genhttp.modules.io.filesystem.FileResourceBuilder
import org.genhttp.modules.io.strings.StringResourceBuilder
import org.genhttp.modules.io.web.WebResourceBuilder

import java.net.URI
import java.nio.file.Path

import kotlin.reflect.KClass

// Allows to build resource instances which can be used by other handlers to generate their content.

/**
 * Generates a resource from the given string.
 *
 * @param data The string that will be returned by the resource (UTF-8-encoded)
 */
public fun Resource.Companion.fromString(data: String): StringResourceBuilder = StringResourceBuilder().content(data)

/**
 * Searches the classpath for a resource with the given path and generates a resource from it.
 *
 * @param path The absolute path of the resource on the classpath (e.g. "web/index.html", the leading slash is optional)
 * @param loader The class loader to search the resource with (defaults to the context class loader of the current thread)
 */
public fun Resource.Companion.fromClasspath(path: String, loader: ClassLoader? = null): ClasspathResourceBuilder {
    val builder = ClasspathResourceBuilder().path(path)

    if (loader != null) {
        builder.loader(loader)
    }

    return builder
}

/**
 * Searches the classpath for a resource with the given name, relative to the package of the given class
 * (like [Class.getResource] does), and generates a resource from it.
 *
 * @param anchor The class whose package and class loader are used to resolve the resource
 * @param name The name of the resource to search for (e.g. "File.txt")
 */
public fun Resource.Companion.fromClasspath(anchor: KClass<*>, name: String): ClasspathResourceBuilder =
    ClasspathResourceBuilder().anchor(anchor).path(name)

/**
 * Generates a resource from the given file.
 *
 * @param file The path of the file to be provided
 */
public fun Resource.Companion.fromFile(file: String): FileResourceBuilder = fromFile(Path.of(file))

/**
 * Generates a resource from the given file.
 *
 * @param file The file to be provided
 */
public fun Resource.Companion.fromFile(file: Path): FileResourceBuilder = FileResourceBuilder().file(file)

/**
 * Creates a resource to be fetched from the given URL.
 *
 * @param source The URI to fetch the resource from
 */
public fun Resource.Companion.fromWeb(source: String): WebResourceBuilder = WebResourceBuilder().source(source)

/**
 * Creates a resource to be fetched from the given URL.
 *
 * @param source The URI to fetch the resource from
 */
public fun Resource.Companion.fromWeb(source: URI): WebResourceBuilder = WebResourceBuilder().source(source)
