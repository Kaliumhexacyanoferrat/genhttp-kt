package org.genhttp.modules.archives.tree

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.Checksum
import org.genhttp.modules.io.guessContentType
import org.genhttp.modules.io.streaming.write

import java.io.InputStream
import java.time.Instant

/**
 * The meta data of an entry of an archive.
 *
 * @param key The key (path within the archive) of the entry
 * @param size The uncompressed size of the entry, if known
 * @param modified The point in time the entry has been modified, if known
 */
internal class ArchiveEntryInfo(val key: String, val size: Long, val modified: Instant?)

internal class ArchiveResource(
    private val archive: Resource,
    entry: ArchiveEntryInfo,
    override val name: String,
    private val streamFactory: EntryStreamFactory
) : Resource {

    private val key: String = entry.key

    // region Get-/Setters

    override val modified: Instant? = entry.modified

    override val contentType: ContentType? = name.guessContentType()

    override val length: Long? = if (entry.size > 0) entry.size else null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long = Checksum.calculate(this)

    override suspend fun getContent(): InputStream {
        val input = archive.getContent()

        try {
            return streamFactory(input, key)
        } catch (e: Throwable) {
            input.close()
            throw e
        }
    }

    override suspend fun write(sink: ResponseSink) {
        getContent().use { stream ->
            stream.write(sink.writer)
        }
    }

    // endregion

}
