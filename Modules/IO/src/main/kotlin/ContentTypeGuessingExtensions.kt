@file:JvmName("ContentTypeGuessingExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType

/**
 * Determines the content type of the resource, either by the type set on the resource
 * or by guessing it from the name of the resource.
 *
 * @receiver The resource to determine the type of
 * @return The type of the resource, or [ContentType.ApplicationForceDownload] if it cannot be determined
 */
public fun Resource.guessContentType(): ContentType =
    contentType ?: name?.guessContentType() ?: ContentType.ApplicationForceDownload

/**
 * Guesses the content type from the extension of the given file name.
 *
 * @receiver The name of the file
 * @return The type matching the extension of the file, or null if it is unknown
 */
public fun String.guessContentType(): ContentType? {
    val separator = maxOf(lastIndexOf('/'), lastIndexOf('\\'))

    val dot = lastIndexOf('.')

    if (dot > separator && dot >= 0) {
        val extension = substring(dot)

        if (extension.length > 1) {
            return ContentTypes[extension.substring(1).lowercase()]
        }
    }

    return null
}

// region Mapping

private val ContentTypes: Map<String, ContentType> = mapOf(
    "css" to ContentType.TextCss,
    "html" to ContentType.TextHtml,
    "htm" to ContentType.TextHtml,
    "txt" to ContentType.TextPlain,
    "conf" to ContentType.TextPlain,
    "config" to ContentType.TextPlain,
    "eot" to ContentType.FontEmbeddedOpenTypeFont,
    "ttf" to ContentType.FontTrueTypeFont,
    "otf" to ContentType.FontOpenTypeFont,
    "woff" to ContentType.FontWoff,
    "woff2" to ContentType.FontWoff2,
    "js" to ContentType.TextJavaScript,
    "mjs" to ContentType.TextJavaScript,
    "ico" to ContentType.ImageIcon,
    "gif" to ContentType.ImageGif,
    "jpeg" to ContentType.ImageJpeg,
    "jpg" to ContentType.ImageJpeg,
    "png" to ContentType.ImagePng,
    "webp" to ContentType.ImageWebp,
    "avif" to ContentType.ImageAvif,
    "bmp" to ContentType.ImageBmp,
    "tiff" to ContentType.ImageTiff,
    "svg" to ContentType.ImageScalableVectorGraphicsXml,
    "svgz" to ContentType.ImageScalableVectorGraphicsCompressed,
    "ogg" to ContentType.AudioOgg,
    "mp3" to ContentType.AudioMpeg,
    "wav" to ContentType.AudioWav,
    "avi" to ContentType.VideoMpeg,
    "3gp" to ContentType.Video3Gpp,
    "3g2" to ContentType.Video3Gpp2,
    "av1" to ContentType.VideoAV1,
    "avc" to ContentType.VideoAvc,
    "dv" to ContentType.VideoDV,
    "mkv" to ContentType.VideoMatroska,
    "mk3d" to ContentType.VideoMatroska3D,
    "mj2" to ContentType.VideoMJ2,
    "mpg" to ContentType.VideoMpeg,
    "mp4" to ContentType.VideoMP4,
    "mpeg" to ContentType.VideoMpeg,
    "mpv" to ContentType.VideoMpv,
    "mov" to ContentType.VideoQuicktime,
    "hdmov" to ContentType.VideoQuicktime,
    "vc1" to ContentType.VideoVC1,
    "vc2" to ContentType.VideoVC2,
    "webm" to ContentType.VideoWebM,
    "csv" to ContentType.TextCsv,
    "rtf" to ContentType.ApplicationRtf,
    "docx" to ContentType.ApplicationOfficeDocumentWordProcessing,
    "pptx" to ContentType.ApplicationOfficeDocumentPresentation,
    "ppsx" to ContentType.ApplicationOfficeDocumentSlideshow,
    "xslx" to ContentType.ApplicationOfficeDocumentSheet,
    "json" to ContentType.ApplicationJson,
    "xml" to ContentType.TextXml,
    "yml" to ContentType.ApplicationYaml,
    "yaml" to ContentType.ApplicationYaml
)

// endregion
