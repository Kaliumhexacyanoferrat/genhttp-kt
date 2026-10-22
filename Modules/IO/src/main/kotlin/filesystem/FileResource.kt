package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.Checksum
import org.genhttp.modules.io.guessContentType
import org.genhttp.modules.io.streaming.write

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * A resource backed by a file of the file system.
 *
 * @param file The file to be provided
 * @param name The name of the resource (defaults to the name of the file)
 * @param contentType The type of the resource (guessed from the name if not specified)
 */
public class FileResource(public val file: Path, name: String?, contentType: ContentType?) : Resource {

    // boxed once per change, as the length is read several times per response
    @Volatile
    private var currentLength: Long?

    init {
        if (!Files.exists(file)) {
            throw FileNotFoundException("File does not exist: ${file.toAbsolutePath()}")
        }

        currentLength = Files.size(file)
    }

    override val name: String? = name ?: file.fileName.toString()

    override val modified: Instant?
        get() = try {
            Files.getLastModifiedTime(file).toInstant()
        } catch (_: IOException) {
            null
        }

    override val contentType: ContentType? = contentType ?: this.name?.guessContentType() ?: ContentType.ApplicationForceDownload

    override val length: Long?
        get() = currentLength

    override suspend fun getContent(): InputStream = Files.newInputStream(file)

    override suspend fun calculateChecksum(): Long {
        updateLength()

        return Checksum.calculate(this)
    }

    override suspend fun write(sink: ResponseSink) {
        Files.newInputStream(file).use { stream ->
            updateLength()

            stream.write(sink.writer)
        }
    }

    private fun updateLength() {
        val size = Files.size(file)

        if (currentLength != size) {
            currentLength = size
        }
    }

}
