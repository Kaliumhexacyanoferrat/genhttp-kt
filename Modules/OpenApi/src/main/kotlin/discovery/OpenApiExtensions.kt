@file:JvmName("OpenApiExtensions")

package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths

import org.genhttp.modules.reflection.operations.Operation

import kotlin.reflect.KType

/**
 * Checks whether a value of the given type might be null.
 */
public fun KType.mightBeNull(): Boolean = isMarkedNullable

/**
 * Finds or creates the path item the given operation is registered with.
 *
 * @param document The document to look up the path item in
 * @param path The path segments of the location the operation has been found at
 * @param operation The operation to find the path item for
 * @return The path item that can be used to register the operation
 */
public fun getPathItem(document: OpenAPI, path: List<String>, operation: Operation): PathItem {
    val stringPath = buildPath(operation.route.name, path, operation.route.isWildcard)

    val paths = document.paths ?: Paths().also { document.paths = it }

    paths[stringPath]?.let { return it }

    val newPath = PathItem()

    paths[stringPath] = newPath

    return newPath
}

/**
 * Builds the path string used to register an operation within an OpenAPI document.
 *
 * @param name The name of the route of the operation
 * @param pathParts The path segments the operation has been found at
 * @param wildcard Whether the operation handles all requests below its route
 * @return The path as used in the document
 */
public fun buildPath(name: String, pathParts: List<String>, wildcard: Boolean): String {
    val builder = StringBuilder("/")

    if (pathParts.isNotEmpty()) {
        builder.append(pathParts.joinToString("/"))
        builder.append('/')
    }

    if (name.isNotEmpty() && name[0] == '/') {
        builder.append(name, 1, name.length)
    } else {
        builder.append(name)
    }

    if (wildcard) {
        builder.append("{remainingPath}")
    }

    return builder.toString()
}
