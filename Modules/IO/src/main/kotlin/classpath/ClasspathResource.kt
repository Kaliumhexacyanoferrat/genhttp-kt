package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.computeChecksum
import org.genhttp.modules.io.streaming.write

import java.io.InputStream
import java.net.URL
import java.time.Instant

/**
 * A resource that is read from the classpath (e.g. a file located in the resources folder of an application
 * or of one of its dependencies).
 */
public class ClasspathResource internal constructor(
    /**
     * The class loader the resource has been resolved with.
     */
    public val source: ClassLoader,
    /**
     * The fully qualified name of the resource, i.e. its absolute path on the classpath (without leading slash).
     */
    public val qualifiedName: String,
    private val url: URL,
    override val name: String?,
    override val contentType: ContentType?,
    override val modified: Instant?
) : Resource {

    /**
     * Creates a resource from the file with the given path on the classpath.
     *
     * @param source The class loader to search the resource with
     * @param path The absolute path of the resource on the classpath (e.g. "web/index.html", the leading slash is optional)
     * @param name The name of the resource, if any
     * @param contentType The type of the resource, if any
     * @param modified The modification date of the resource, if known
     * @throws IllegalStateException If the resource does not exist
     */
    public constructor(source: ClassLoader, path: String, name: String?, contentType: ContentType?, modified: Instant?) :
            this(source, normalizeClasspathPath(path), locate(source, path), name, contentType, modified)

    private var checksum: Long? = null

    override val length: Long? = url.getContentLength()

    override suspend fun getContent(): InputStream = tryGetStream()

    override suspend fun calculateChecksum(): Long {
        var result = checksum

        if (result == null) {
            result = tryGetStream().use { it.computeChecksum() }

            checksum = result
        }

        return result
    }

    override suspend fun write(sink: ResponseSink) {
        tryGetStream().use { it.write(sink.writer) }
    }

    private fun tryGetStream(): InputStream = url.openStream()

    internal companion object {

        /**
         * Resolves the given path with the loader, failing if it does not exist.
         */
        fun locate(source: ClassLoader, path: String): URL =
            source.getResource(normalizeClasspathPath(path)) ?: throw IllegalStateException("Resource '$path' does not exist on the classpath")

    }

}
