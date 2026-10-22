package org.genhttp.modules.files

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.files.single.ResourceAssetBuilder
import org.genhttp.modules.io.fromFile

import java.nio.file.Path

/**
 * Serves a single resource or file.
 */
public object Asset {

    /**
     * Serves the given resource.
     *
     * @param resource The resource to be served
     * @return The handler to serve the resource
     */
    public fun from(resource: Builder<Resource>): ResourceAssetBuilder = from(resource.build())

    /**
     * Serves the given resource.
     *
     * @param resource The resource to be served
     * @return The handler to serve the resource
     */
    public fun from(resource: Resource): ResourceAssetBuilder = ResourceAssetBuilder(resource)

    /**
     * Serves the given file.
     *
     * @param file The file to be served
     * @return The handler to serve the file
     */
    public fun from(file: Path): ResourceAssetBuilder = from(Resource.fromFile(file))

    /**
     * Serves the given file.
     *
     * @param filePath The path of the file to be served
     * @return The handler to serve the file
     */
    public fun from(filePath: String): ResourceAssetBuilder = from(Resource.fromFile(filePath))

}
