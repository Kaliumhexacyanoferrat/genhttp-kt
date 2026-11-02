package org.genhttp.modules.mcp

import tools.jackson.core.JsonGenerator

import java.nio.ByteBuffer

/**
 * The result of a tool call, allowing full control over the content sent to the client.
 *
 * Tools can also return a [String] (sent as text) or any other object (sent as JSON, both as text and as
 * structured content).
 *
 * @param content The content blocks to be presented to the model
 * @property structured Data to be sent as structured content, serialized to JSON. If no content blocks are
 * given, the serialized data will also be sent as a text block, as recommended by the specification.
 * @property isError Whether the call failed
 */
public class ToolResult(
    content: List<ToolContent>?,
    public val structured: Any? = null,
    public val isError: Boolean = false
) {

    /**
     * The content blocks to be presented to the model.
     */
    public val content: List<ToolContent> = content ?: NoContent

    public companion object {

        private val NoContent: List<ToolContent> = emptyList()

        /**
         * A result consisting of a single text block, such as Markdown.
         *
         * @param text The text to be read by the model
         */
        public fun text(text: String): ToolResult = ToolResult(listOf(TextContent(text)))

        /**
         * A result consisting of the given content blocks.
         *
         * @param content The content blocks to be presented to the model
         */
        public fun from(vararg content: ToolContent): ToolResult = ToolResult(content.toList())

        /**
         * A result carrying the given data as JSON.
         *
         * @param data The data to be serialized
         */
        public fun json(data: Any): ToolResult = ToolResult(null, data)

        /**
         * A failed call, with an explanation for the model.
         *
         * @param message The reason the call failed
         */
        public fun error(message: String): ToolResult = ToolResult(listOf(TextContent(message)), isError = true)

    }

}

/**
 * A block of content returned by a tool.
 */
public sealed class ToolContent {

    internal abstract fun write(generator: JsonGenerator)

}

/**
 * Text, such as plain text or Markdown.
 *
 * @property text The text to be read by the model
 */
public data class TextContent(val text: String) : ToolContent() {

    override fun write(generator: JsonGenerator) {
        generator.writeStartObject()
        generator.writeStringProperty("type", "text")
        generator.writeStringProperty("text", text)
        generator.writeEndObject()
    }

}

/**
 * An image.
 *
 * @property data The binary data of the image (the content between position and limit is sent, the buffer
 * is not modified)
 * @property mimeType The type of the image, such as "image/png"
 */
public data class ImageContent(val data: ByteBuffer, val mimeType: String) : ToolContent() {

    override fun write(generator: JsonGenerator): Unit = BinaryContent.write(generator, "image", data, mimeType)

}

/**
 * An audio clip.
 *
 * @property data The binary data of the clip (the content between position and limit is sent, the buffer
 * is not modified)
 * @property mimeType The type of the clip, such as "audio/wav"
 */
public data class AudioContent(val data: ByteBuffer, val mimeType: String) : ToolContent() {

    override fun write(generator: JsonGenerator): Unit = BinaryContent.write(generator, "audio", data, mimeType)

}

/**
 * A link to a resource the client can fetch.
 *
 * @property uri The URI of the resource
 * @property name The name of the resource
 * @property title A human-readable name of the resource
 * @property description Describes what the resource contains
 * @property mimeType The type of the resource, if known
 */
public data class ResourceLinkContent(
    val uri: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null
) : ToolContent() {

    override fun write(generator: JsonGenerator) {
        generator.writeStartObject()
        generator.writeStringProperty("type", "resource_link")
        generator.writeStringProperty("uri", uri)
        generator.writeStringProperty("name", name)

        if (title != null) {
            generator.writeStringProperty("title", title)
        }

        if (description != null) {
            generator.writeStringProperty("description", description)
        }

        if (mimeType != null) {
            generator.writeStringProperty("mimeType", mimeType)
        }

        generator.writeEndObject()
    }

}

internal object BinaryContent {

    fun write(generator: JsonGenerator, type: String, data: ByteBuffer, mimeType: String) {
        val view = data.duplicate()
        val bytes = ByteArray(view.remaining())

        view.get(bytes)

        generator.writeStartObject()
        generator.writeStringProperty("type", type)
        generator.writeName("data")
        generator.writeBinary(bytes)
        generator.writeStringProperty("mimeType", mimeType)
        generator.writeEndObject()
    }

}
