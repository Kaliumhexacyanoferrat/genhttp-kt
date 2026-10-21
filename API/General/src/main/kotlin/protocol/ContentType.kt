package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * Represents an HTTP Content-Type.
 */
public class ContentType : MemoryView {

    /**
     * Creates a content type on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates a content type on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates a content type from the given string (e.g. "text/html").
     */
    public constructor(value: String) : super(value)

    // region Functionality

    /**
     * Removes any addition from the given content type value (such as "; charset=utf-8").
     *
     * @return The content type without any additional options
     */
    public fun withoutOptions(): ContentType {
        val buffer = bytes
        val start = buffer.position()
        val length = buffer.remaining()

        var index = -1

        for (i in 0 until length) {
            if (buffer.get(start + i) == ';'.code.toByte()) {
                index = i
                break
            }
        }

        if (index < 0) {
            return this
        }

        var end = index

        while (end > 0 && buffer.get(start + end - 1) == ' '.code.toByte()) {
            end--
        }

        val trimmed = ByteArray(end)
        buffer.get(start, trimmed)

        return ContentType(trimmed)
    }

    // endregion

    /**
     * Well-known content types.
     */
    public companion object {

        /** A HTML page. */
        public val TextHtml: ContentType = ContentType("text/html")

        /** A stylesheet. */
        public val TextCss: ContentType = ContentType("text/css")

        /** A human-readable YAML file. */
        @Deprecated("Please use 'ApplicationYaml' instead", ReplaceWith("ContentType.ApplicationYaml"))
        public val TextYaml: ContentType = ContentType("text/yaml")

        /** A JavaScript source file. */
        @Deprecated("Please use 'TextJavaScript' instead", ReplaceWith("ContentType.TextJavaScript"))
        public val ApplicationJavaScript: ContentType = ContentType("application/javascript")

        /** A JSON file. */
        public val ApplicationJson: ContentType = ContentType("application/json")

        /** A YAML file. */
        public val ApplicationYaml: ContentType = ContentType("application/yaml")

        /** A PNG image. */
        public val ImagePng: ContentType = ContentType("image/png")

        /** A WebP image. */
        public val ImageWebp: ContentType = ContentType("image/webp")

        /** An AVIF image. */
        public val ImageAvif: ContentType = ContentType("image/avif")

        /** A BMP image. */
        public val ImageBmp: ContentType = ContentType("image/bmp")

        /** A JPG image. */
        public val ImageJpeg: ContentType = ContentType("image/jpeg")

        /** A JPG image. */
        @Deprecated("Please use 'ImageJpeg' instead", ReplaceWith("ContentType.ImageJpeg"))
        public val ImageJpg: ContentType
            get() = ImageJpeg

        /** A GIF image. */
        public val ImageGif: ContentType = ContentType("image/gif")

        /** A download. */
        public val ApplicationForceDownload: ContentType = ContentType("application/force-download")

        /** Anything else - data. */
        public val ApplicationOctetStream: ContentType = ContentType("application/octet-stream")

        /** A MP4 audio file. */
        public val AudioMp4: ContentType = ContentType("audio/mp4")

        /** A OGG audio file. */
        public val AudioOgg: ContentType = ContentType("audio/ogg")

        /** A MPEG audio file. */
        public val AudioMpeg: ContentType = ContentType("audio/mpeg")

        /** A TIFF image. */
        public val ImageTiff: ContentType = ContentType("image/tiff")

        /** A CSV file. */
        public val TextCsv: ContentType = ContentType("text/csv")

        /** A richtext file. */
        public val TextRichText: ContentType = ContentType("text/richtext")

        /** A RTF file. */
        public val ApplicationRtf: ContentType = ContentType("application/rtf")

        /** Plain text. */
        public val TextPlain: ContentType = ContentType("text/plain")

        /** A XML file. */
        public val TextXml: ContentType = ContentType("text/xml")

        /** A XML file. */
        public val ApplicationXml: ContentType = ContentType("application/xml")

        /** A JavaScript file. */
        public val TextJavaScript: ContentType = ContentType("text/javascript")

        /** A SSE stream. */
        public val TextEventStream: ContentType = ContentType("text/event-stream")

        /** A uncompressed audio file. */
        public val AudioWav: ContentType = ContentType("audio/wav")

        /** Word processing document (e.g. docx). */
        public val ApplicationOfficeDocumentWordProcessing: ContentType = ContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        /** A presentation (e.g. pptx). */
        public val ApplicationOfficeDocumentPresentation: ContentType = ContentType("application/vnd.openxmlformats-officedocument.presentationml.presentation")

        /** A slideshow (e.g. .ppsx). */
        public val ApplicationOfficeDocumentSlideshow: ContentType = ContentType("application/vnd.openxmlformats-officedocument.presentationml.slideshow")

        /** A sheet (e.g. .xlsx). */
        public val ApplicationOfficeDocumentSheet: ContentType = ContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

        /** An icon. */
        public val ImageIcon: ContentType = ContentType("image/vnd.microsoft.icon")

        /** Microsoft embedded OTF. */
        public val FontEmbeddedOpenTypeFont: ContentType = ContentType("application/vnd.ms-fontobject")

        /** True type font. */
        public val FontTrueTypeFont: ContentType = ContentType("font/ttf")

        /** Woff font. */
        public val FontWoff: ContentType = ContentType("font/woff")

        /** Woff2 font. */
        public val FontWoff2: ContentType = ContentType("font/woff2")

        /** Open type font. */
        public val FontOpenTypeFont: ContentType = ContentType("font/otf")

        /** SVG. */
        public val ImageScalableVectorGraphics: ContentType = ContentType("image/svg")

        /** SVG XML. */
        public val ImageScalableVectorGraphicsXml: ContentType = ContentType("image/svg+xml")

        /** Compressed SVG. */
        public val ImageScalableVectorGraphicsCompressed: ContentType = ContentType("image/svgz")

        /** Form encoded. */
        public val ApplicationWwwFormUrlEncoded: ContentType = ContentType("application/x-www-form-urlencoded")

        /** Protobuf. */
        public val ApplicationProtobuf: ContentType = ContentType("application/protobuf")

        /** PDF. */
        public val ApplicationPdf: ContentType = ContentType("application/pdf")

        /** 3GPP video. */
        public val Video3Gpp: ContentType = ContentType("video/3gpp")

        /** 3GPP2 video. */
        public val Video3Gpp2: ContentType = ContentType("video/3gpp2")

        /** AV1 video. */
        public val VideoAV1: ContentType = ContentType("video/av1")

        /** AVC video. */
        public val VideoAvc: ContentType = ContentType("video/av")

        /** DV video. */
        public val VideoDV: ContentType = ContentType("video/dv")

        /** H261 video. */
        public val VideoH261: ContentType = ContentType("video/H261")

        /** H263 video. */
        public val VideoH263: ContentType = ContentType("video/H263")

        /** H264 video. */
        public val VideoH264: ContentType = ContentType("video/H264")

        /** H265 video. */
        public val VideoH265: ContentType = ContentType("video/H265")

        /** H266 video. */
        public val VideoH266: ContentType = ContentType("video/H266")

        /** Matroska. */
        public val VideoMatroska: ContentType = ContentType("video/matroska")

        /** Matroska 3D. */
        public val VideoMatroska3D: ContentType = ContentType("video/matroska-3d")

        /** MJ2. */
        public val VideoMJ2: ContentType = ContentType("video/mj2")

        /** MP4 video. */
        public val VideoMP4: ContentType = ContentType("video/mp4")

        /** MPEG video. */
        public val VideoMpeg: ContentType = ContentType("video/mpeg")

        /** MPEG4 generic. */
        public val VideoMpeg4Generic: ContentType = ContentType("video/mpeg4-generic")

        /** MPV. */
        public val VideoMpv: ContentType = ContentType("video/MPV")

        /** Quicktime. */
        public val VideoQuicktime: ContentType = ContentType("video/quicktime")

        /** Raw video. */
        public val VideoRaw: ContentType = ContentType("video/raw")

        /** VC1. */
        public val VideoVC1: ContentType = ContentType("video/vc1")

        /** VC2. */
        public val VideoVC2: ContentType = ContentType("video/vc2")

        /** VP8. */
        public val VideoVP8: ContentType = ContentType("video/VP8")

        /** VP9. */
        public val VideoVP9: ContentType = ContentType("video/VP9")

        /** WebM. */
        public val VideoWebM: ContentType = ContentType("video/webm")

    }

}
