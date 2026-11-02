package org.genhttp.modules.files.multi

import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.filesystem.FileResource
import org.genhttp.modules.io.streaming.ResourceContent

import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class BuiltInFileAssetHandler(
    directory: Path,
    algorithms: List<CompressionAlgorithm>,
    separator: Char
) : AbstractAssetsHandler(algorithms, separator) {

    private val root: Path = directory.toAbsolutePath().normalize()

    override suspend fun resolve(target: RequestTarget, contentType: ContentType?, contentEncoding: ByteBuffer?): ResponseContent? {
        val file = resolveFile(target)

        if (file != null) {
            val resource = FileResource(file, file.fileName.toString(), contentType)

            return ResourceContent(resource, contentType, contentEncoding)
        }

        return null
    }

    private fun resolveFile(target: RequestTarget): Path? {
        var length = 0
        var segments = 0

        while (true) {
            val segment = target.next(segments) ?: break

            length += segment.length + 1
            segments++
        }

        if (segments == 0) {
            return null
        }

        val buffer = CharBuffer.allocate(length)

        for (index in 0 until segments) {
            if (index > 0) {
                buffer.put(File.separatorChar)
            }

            target.next(index)?.decode(buffer)
        }

        buffer.flip()

        try {
            val file = root.resolve(buffer.toString()).normalize()

            // never leave the mounted directory
            return if (file.startsWith(root) && Files.isRegularFile(file)) file else null
        } catch (_: InvalidPathException) {
            return null
        }
    }

}
