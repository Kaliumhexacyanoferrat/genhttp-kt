package org.genhttp.modules.files.multi

import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.filesystem.FileResource
import org.genhttp.modules.io.streaming.ResourceContent

import java.nio.ByteBuffer
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
        val path = target.asString(decode = true, remainingOnly = true)

        val file = resolveFile(path)

        if (file != null) {
            val resource = FileResource(file, file.fileName.toString(), contentType)

            return ResourceContent(resource, contentType, contentEncoding)
        }

        return null
    }

    private fun resolveFile(requestPath: String): Path? {
        val relative = requestPath.trimStart('/').trimStart('\\')

        if (relative.isEmpty()) {
            return null
        }

        try {
            val file = root.resolve(relative).normalize()

            // never leave the mounted directory
            return if (file.startsWith(root) && Files.isRegularFile(file)) file else null
        } catch (_: InvalidPathException) {
            return null
        }
    }

}
