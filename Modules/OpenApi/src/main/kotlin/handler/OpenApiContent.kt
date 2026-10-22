package org.genhttp.modules.openapi.handler

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.nio.ByteBuffer

internal class OpenApiContent(private val document: ReturnDocument, private val format: OpenApiFormat) : ResponseContent {

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = if (format == OpenApiFormat.Json) ContentType.ApplicationJson else ContentType.ApplicationYaml

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = document.checksum

    override suspend fun write(sink: ResponseSink) {
        val text = if (format == OpenApiFormat.Json) {
            Json.pretty(document.document)
        } else {
            Yaml.pretty(document.document)
        }

        sink.stream.write(text.toByteArray(Charsets.UTF_8))
    }

    // endregion

}
