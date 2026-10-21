@file:JvmName("ResourceTree")

package org.genhttp.modules.io

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.modules.io.classpath.ClasspathResourceTreeBuilder
import org.genhttp.modules.io.classpath.joinClasspathPath
import org.genhttp.modules.io.classpath.normalizeClasspathPath
import org.genhttp.modules.io.classpath.packagePath
import org.genhttp.modules.io.filesystem.DirectoryTreeBuilder

import java.nio.file.Path

import kotlin.reflect.KClass

// Provides a folder-like structure that can be used to generate responses.

/**
 * Creates a resource tree that will provide all classpath resources found in the given directory
 * (across all classpath roots, i.e. directories and JAR files).
 *
 * @param root The absolute path of the directory on the classpath (e.g. "web/assets", the leading slash is optional)
 * @param loader The class loader to enumerate the content with (defaults to the context class loader of the current thread)
 */
public fun ResourceTree.Companion.fromClasspath(root: String, loader: ClassLoader? = null): ClasspathResourceTreeBuilder {
    val builder = ClasspathResourceTreeBuilder().root(root)

    if (loader != null) {
        builder.loader(loader)
    }

    return builder
}

/**
 * Creates a resource tree that will provide all classpath resources found in a directory relative to the
 * package of the given class.
 *
 * @param anchor The class whose package and class loader are used to resolve the directory
 * @param root The directory relative to the package of the anchor (the package itself, if empty)
 */
public fun ResourceTree.Companion.fromClasspath(anchor: KClass<*>, root: String = ""): ClasspathResourceTreeBuilder {
    val builder = ClasspathResourceTreeBuilder().root(joinClasspathPath(anchor.java.packagePath(), normalizeClasspathPath(root)))

    anchor.java.classLoader?.let { builder.loader(it) }

    return builder
}

/**
 * Creates a resource tree from the given directory.
 *
 * @param directory The full path of the directory to be provided
 */
public fun ResourceTree.Companion.fromDirectory(directory: String): DirectoryTreeBuilder = fromDirectory(Path.of(directory))

/**
 * Creates a resource tree from the given directory.
 *
 * @param directory The directory to be provided
 */
public fun ResourceTree.Companion.fromDirectory(directory: Path): DirectoryTreeBuilder = DirectoryTreeBuilder().directory(directory)
