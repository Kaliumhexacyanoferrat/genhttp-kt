package org.genhttp.modules.reverseproxy.http

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.copyPooled

import java.io.InputStream
import java.io.OutputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer

internal class ClientResponseContent(private val message: HttpResponse<InputStream>) : ResponseContent, AutoCloseable {

    private var closed = false

    override val length: Long?
        get() {
            val length = message.headers().firstValueAsLong("Content-Length")

            return if (length.isPresent) length.asLong else null
        }

    override val type: ContentType?
        get() {
            val type = message.headers().firstValue("Content-Type")

            return if (type.isPresent) ContentType(type.get()) else ContentType.ApplicationOctetStream
        }

    override val encoding: ByteBuffer?
        get() {
            val encoding = message.headers().firstValue("Content-Encoding")

            return if (encoding.isPresent) ByteBuffer.wrap(encoding.get().toByteArray(Charsets.US_ASCII)) else null
        }

    override suspend fun calculateChecksum(): Long? = null

    suspend fun write(target: OutputStream, bufferSize: Int) {
        message.body().use { source ->
            source.copyPooled(target, bufferSize)
        }
    }

    override suspend fun write(sink: ResponseSink) {
        write(sink.stream, 8192)
    }

    override fun close() {
        if (!closed) {
            closed = true

            message.body().close()
        }
    }

}
