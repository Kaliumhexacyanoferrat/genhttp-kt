package org.genhttp.modules.archives.tree

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.io.streaming.StreamWithDependency
import org.genhttp.modules.io.tracking.ChangeTrackingResource

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * A tree implementation that expands the given archive into a fully browsable tree instance.
 *
 * Supports ZIP, TAR (optionally compressed with gzip or bzip2) and 7z archives.
 *
 * @param source The resource granting access to the archive
 */
public class ArchivedTree(private val source: ChangeTrackingResource) : ResourceTree {

    private val updateLock = Mutex()

    @Volatile
    private var root: ArchiveNode? = null

    override val modified: Instant?
        get() = source.modified

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? = ensureRoot().tryGetNode(segment)

    override suspend fun getNodes(): Collection<ResourceNode> = ensureRoot().getNodes()

    override suspend fun tryGetResource(segment: PathSegment): Resource? = ensureRoot().tryGetResource(segment)

    override suspend fun getResources(): Collection<Resource> = ensureRoot().getResources()

    private suspend fun ensureRoot(): ArchiveNode {
        if (checkUpdateNeeded()) {
            updateLock.withLock {
                if (checkUpdateNeeded()) {
                    root = loadArchive()
                }
            }
        }

        return root!!
    }

    private suspend fun loadArchive(): ArchiveNode {
        source.getContent().use { input ->
            val (format, stream) = open(input)

            return when (format) {
                Format.Zip -> loadZip(stream)
                Format.Tar -> loadTar(stream)
                Format.SevenZip -> loadSevenZip(stream)
            }
        }
    }

    private fun loadZip(input: InputStream): ArchiveNode {
        val root = ArchiveNode(null, null)

        // the central directory is needed to know the sizes of all entries, which requires random access
        val file = TemporaryFile.from(input)

        try {
            ZipFile.builder().setPath(file.path).get().use { archive ->
                for (entry in archive.entries) {
                    addEntry(root, entry, ::getZipStream)
                }
            }
        } finally {
            file.close()
        }

        return root
    }

    private fun loadTar(input: InputStream): ArchiveNode {
        val root = ArchiveNode(null, null)

        val archive = TarArchiveInputStream(input)

        while (true) {
            val entry = archive.nextEntry ?: break

            addEntry(root, entry, ::getTarStream)
        }

        return root
    }

    private fun loadSevenZip(input: InputStream): ArchiveNode {
        val root = ArchiveNode(null, null)

        val file = TemporaryFile.from(input)

        try {
            SevenZFile.builder().setPath(file.path).get().use { archive ->
                for (entry in archive.entries) {
                    addEntry(root, entry, ::getSevenZipStream)
                }
            }
        } finally {
            file.close()
        }

        return root
    }

    private fun addEntry(root: ArchiveNode, entry: ArchiveEntry, streamFactory: EntryStreamFactory) {
        val info = ArchiveEntryInfo(entry.name, entry.size, getModified(entry))

        if (entry.isDirectory) {
            addDirectory(root, info)
        } else {
            addFile(root, info, streamFactory)
        }
    }

    private fun addDirectory(root: ArchiveNode, entry: ArchiveEntryInfo) {
        if (entry.key == "./") return

        val parts = getParts(entry)

        var current = root

        for (part in parts) {
            current = current.getOrCreate(part)
        }

        current.adapt(entry)
    }

    private fun addFile(root: ArchiveNode, entry: ArchiveEntryInfo, streamFactory: EntryStreamFactory) {
        val parts = getParts(entry)

        var node = root

        for (i in 0 until parts.size - 1) {
            node = node.getOrCreate(parts[i])
        }

        node.addFile(parts.last(), entry, source, streamFactory)
    }

    private fun getZipStream(input: InputStream, key: String): InputStream {
        val (_, stream) = open(input)

        val archive = createZipStream(stream)

        while (true) {
            val entry = archive.nextEntry ?: break

            if (!entry.isDirectory && entry.name == key) {
                return archive
            }
        }

        archive.close()

        throw IllegalStateException("Unable to find resource '$key' in archive")
    }

    private fun getTarStream(input: InputStream, key: String): InputStream {
        val (_, stream) = open(input)

        val archive = TarArchiveInputStream(stream)

        while (true) {
            val entry = archive.nextEntry ?: break

            if (!entry.isDirectory && entry.name == key) {
                return archive
            }
        }

        archive.close()

        throw IllegalStateException("Unable to find resource '$key' in archive")
    }

    private fun getSevenZipStream(input: InputStream, key: String): InputStream {
        val (_, stream) = open(input)

        val file = TemporaryFile.from(stream)

        try {
            val archive = SevenZFile.builder().setPath(file.path).get()

            try {
                for (entry in archive.entries) {
                    if (!entry.isDirectory && entry.name == key) {
                        return StreamWithDependency(archive.getInputStream(entry), AutoCloseable {
                            try {
                                archive.close()
                            } finally {
                                file.close()
                            }
                        })
                    }
                }
            } catch (e: Throwable) {
                archive.close()
                throw e
            }

            archive.close()
        } catch (e: Throwable) {
            file.close()
            throw e
        }

        file.close()

        throw IllegalStateException("Unable to find resource '$key' in archive")
    }

    private suspend fun checkUpdateNeeded(): Boolean = root == null || source.checkChanged()

    private fun getParts(entry: ArchiveEntryInfo): List<String> {
        var key = entry.key

        if (key.startsWith('.')) {
            key = key.substring(1)
        }

        return key.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    }

    private fun getModified(entry: ArchiveEntry): Instant? {
        val time = entry.lastModifiedDate?.time

        return if (time == null || time < 0) null else Instant.ofEpochMilli(time)
    }

    // region Format detection

    private enum class Format {
        Zip,
        Tar,
        SevenZip
    }

    private fun createZipStream(input: InputStream): ZipArchiveInputStream =
        ZipArchiveInputStream(input, "UTF8", true, true)

    /**
     * Detects the format of the archive (and unwraps gzip and bzip2 compression), returning a stream
     * positioned at the beginning of the (uncompressed) archive.
     */
    private fun open(input: InputStream): Pair<Format, InputStream> {
        var stream = BufferedInputStream(input)

        stream.mark(3)

        val signature = stream.readNBytes(3)

        stream.reset()

        if (signature.size >= 2 && signature[0] == 0x1F.toByte() && signature[1] == 0x8B.toByte()) {
            stream = BufferedInputStream(GzipCompressorInputStream.builder().setInputStream(stream).setDecompressConcatenated(true).get())
        } else if (signature.size == 3 && signature[0] == 'B'.code.toByte() && signature[1] == 'Z'.code.toByte() && signature[2] == 'h'.code.toByte()) {
            stream = BufferedInputStream(BZip2CompressorInputStream(stream, true))
        }

        val format = try {
            ArchiveStreamFactory.detect(stream)
        } catch (e: ArchiveException) {
            throw IOException("Unable to detect the format of the archive", e)
        }

        return when (format) {
            ArchiveStreamFactory.ZIP -> Format.Zip to stream
            ArchiveStreamFactory.TAR -> Format.Tar to stream
            ArchiveStreamFactory.SEVEN_Z -> Format.SevenZip to stream
            else -> throw IOException("Unsupported archive format '$format'")
        }
    }

    // endregion

    /**
     * ZIP (when listed) and 7z archives need random access, so they are copied to a temporary file while they are read.
     */
    private class TemporaryFile private constructor(val path: Path) : AutoCloseable {

        override fun close() {
            Files.deleteIfExists(path)
        }

        companion object {

            fun from(input: InputStream): TemporaryFile {
                val path = Files.createTempFile("genhttp-archive", ".tmp")

                try {
                    Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Throwable) {
                    Files.deleteIfExists(path)
                    throw e
                }

                return TemporaryFile(path)
            }

        }

    }

}
