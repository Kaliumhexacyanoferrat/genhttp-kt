package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.ContentType

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Builds a resource that provides the content of a file.
 */
public class FileResourceBuilder : ResourceBuilder<FileResourceBuilder> {

    private var file: Path? = null

    private var name: String? = null

    private var contentType: ContentType? = null

    /**
     * Sets the file to be provided by the resource.
     *
     * @param file The file to be provided
     */
    public fun file(file: Path): FileResourceBuilder {
        this.file = file
        return this
    }

    override fun name(name: String): FileResourceBuilder {
        this.name = name
        return this
    }

    override fun type(contentType: ContentType): FileResourceBuilder {
        this.contentType = contentType
        return this
    }

    /**
     * Not supported, as the modification date of a file cannot be changed.
     *
     * @throws UnsupportedOperationException Always
     */
    override fun modified(modified: Instant): FileResourceBuilder =
        throw UnsupportedOperationException("Modification date of file resources cannot be changed")

    override fun build(): Resource {
        val file = file ?: throw BuilderMissingPropertyException("file")

        if (!Files.exists(file)) {
            throw FileNotFoundException("The given file does not exist: ${file.toAbsolutePath()}")
        }

        return FileResource(file, name, contentType)
    }

}
