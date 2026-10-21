package org.genhttp.testing.acceptance.utilities

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.errorhandling.ErrorHandler
import org.genhttp.modules.errorhandling.provider.ErrorSentry
import org.genhttp.modules.io.fromClasspath

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.time.Instant

import kotlin.test.assertTrue

/**
 * A handler responding via the given function.
 */
class FunctionalHandler(private val responseProvider: ((Request) -> Response?)? = null) : Handler {

    override suspend fun prepare(server: Server) {}

    override suspend fun handle(request: Request): Response? = responseProvider?.invoke(request)

}

/**
 * A handler responding via the given suspending function.
 */
class AsyncFunctionalHandler(private val responseProvider: (suspend (Request) -> Response?)? = null) : Handler {

    override suspend fun prepare(server: Server) {}

    override suspend fun handle(request: Request): Response? = responseProvider?.invoke(request)

}

/**
 * Wraps an existing handler so concerns can be added (`HandlerBuilder` in the upstream tests).
 */
open class WrappingHandlerBuilder(private val handler: Handler) : HandlerBuilder.WithConcerns<WrappingHandlerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    override fun add(concern: ConcernBuilder): WrappingHandlerBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler = Concerns.chain(concerns, handler)

}

/**
 * Checks that concerns can be added to a builder.
 */
object Chain {

    fun <T : HandlerBuilder.WithConcerns<T>> works(builder: HandlerBuilder.WithConcerns<T>) {
        builder.add(ErrorHandler.html())
        assertTrue(builder.build() is ErrorSentry<*>)
    }

}

/**
 * Writes files used by tests.
 */
object FileUtil {

    fun writeText(path: Path, content: String) {
        Files.write(path, content.toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC)
    }

    fun writeText(path: String, content: String) = writeText(Path.of(path), content)

    suspend fun writeTextAsync(path: Path, content: String) {
        kotlinx.coroutines.delay(100)
        writeText(path, content)
    }

    suspend fun writeTextAsync(path: String, content: String) = writeTextAsync(Path.of(path), content)

}

/**
 * A resource that does not provide meta information such as a length or a modification date.
 */
open class NonSeekableResource(private val source: Resource) : Resource {

    override val name: String? = null

    override val modified: Instant? = null

    override val contentType: ContentType? = null

    override val length: Long? = null

    override suspend fun calculateChecksum(): Long = 0

    override suspend fun getContent(): InputStream = NonSeekableStream(source.getContent())

    override suspend fun write(sink: ResponseSink) {
        getContent().use { it.transferTo(sink.stream) }
    }

}

/**
 * A stream that does not support marking (JVM streams are never seekable).
 */
open class NonSeekableStream(private val inner: InputStream) : InputStream() {

    override fun read(): Int = inner.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)

    override fun markSupported(): Boolean = false

    override fun close() = inner.close()

}

/**
 * Provides the certificate used by the security tests.
 */
object Security {

    suspend fun getCertificate(): KeyStore.PrivateKeyEntry {
        val store = KeyStore.getInstance("PKCS12")

        Resource.fromClasspath("Resources/Certificate.pfx").build().getContent().use {
            store.load(it, CharArray(0))
        }

        val alias = store.aliases().toList().first { store.isKeyEntry(it) }

        return store.getEntry(alias, KeyStore.PasswordProtection(CharArray(0))) as KeyStore.PrivateKeyEntry
    }

}
