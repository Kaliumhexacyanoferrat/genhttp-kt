package org.genhttp.modules.io.ranges

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.services.ApiRequestHeader
import org.genhttp.api.content.services.ApiResponse
import org.genhttp.api.content.services.ApiResponseHeader
import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.streaming.ResourceContent

/**
 * Adds support for partial responses (HTTP range requests) to the handler it wraps, as long as the length of
 * the generated content is known.
 *
 * @param content The handler to be wrapped by this concern
 */
public class RangeSupportConcern(override val content: Handler) : DocumentedConcern {

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        if (operation.method == RequestMethod.Get || operation.method == RequestMethod.Head) {
            operation.add(ApiRequestHeader(name = "Range", description = "Requests a part of the content (e.g. bytes=0-1023), supported if the length of the content is known"))
                .add(ApiResponse(status = ResponseStatus.PartialContent, description = "The requested part of the content"))
                .add(ApiResponse(status = ResponseStatus.RequestedRangeNotSatisfiable, description = "The requested range is not within the content", contentType = "text/plain"))
                .add(ApiResponseHeader(name = "Accept-Ranges", description = "Set to 'bytes' if parts of the content can be requested", status = arrayOf(ResponseStatus.Ok)))
                .add(ApiResponseHeader(name = "Content-Range", description = "The part of the content contained in the response (e.g. bytes 0-1023/4096)", status = arrayOf(ResponseStatus.PartialContent)))
                .add(ApiResponseHeader(name = "Content-Range", description = "The total length of the content (e.g. bytes */4096)", status = arrayOf(ResponseStatus.RequestedRangeNotSatisfiable)))
        }
    }

    override suspend fun handle(request: Request): Response? {
        val header = request.header

        if (header.method == RequestMethod.Get || header.method == RequestMethod.Head) {
            val requested = header.headers.getEntry("Range")

            val response = content.handle(request)

            if (response != null) {
                if (response.status == ResponseStatus.Ok) {
                    val responseContent = response.content

                    if (responseContent != null) {
                        val length = responseContent.length

                        if (length != null) {
                            response.rebuild().header(AcceptRangesHeader, BytesValue)

                            if (requested != null) {
                                val match = Pattern.find(requested)

                                if (match != null) {
                                    val startString = match.groupValues[1]
                                    val endString = match.groupValues[2]

                                    val start = if (startString.isEmpty()) null else startString.toLong()
                                    val end = if (endString.isEmpty()) null else endString.toLong()

                                    if (start != null || end != null) {
                                        if (end == null) {
                                            return getRangeFromStart(request, response, length, start!!)
                                        }
                                        if (start == null) {
                                            return getRangeFromEnd(request, response, length, end)
                                        }
                                        return getFullRange(request, response, length, start, end)
                                    }
                                } else {
                                    return notSatisfiable(request, length)
                                }
                            }
                        }
                    }
                }
            }

            return response
        }

        return content.handle(request)
    }

    private fun getRangeFromStart(request: Request, response: Response, length: Long, start: Long): Response {
        if (start > length) {
            return notSatisfiable(request, length)
        }

        return getRange(response, start, length - 1, length)
    }

    private fun getRangeFromEnd(request: Request, response: Response, length: Long, end: Long): Response {
        if (end > length) {
            return notSatisfiable(request, length)
        }

        return getRange(response, length - end, length - 1, length)
    }

    private fun getFullRange(request: Request, response: Response, length: Long, start: Long, end: Long): Response {
        if (start > end || end >= length) {
            return notSatisfiable(request, length)
        }

        return getRange(response, start, end, length)
    }

    private fun getRange(response: Response, actualStart: Long, actualEnd: Long, totalLength: Long): Response {
        response.rebuild()
            .status(ResponseStatus.PartialContent)
            .header("Content-Range", "bytes $actualStart-$actualEnd/$totalLength")
            .content(RangedContent(response.content!!, actualStart, actualEnd))

        return response
    }

    private fun notSatisfiable(request: Request, totalLength: Long): Response {
        val content = Resource.fromString("Requested length cannot be satisfied (available = $totalLength bytes)").build()

        return request.respond()
            .status(ResponseStatus.RequestedRangeNotSatisfiable)
            .header("Content-Range", "bytes */$totalLength")
            .content(ResourceContent(content, ContentType.TextPlain))
            .build()
    }

    private companion object {

        private val Pattern = Regex("""^\s*bytes\s*=\s*([0-9]*)-([0-9]*)\s*$""", RegexOption.IGNORE_CASE)

        private val AcceptRangesHeader = ByteString("Accept-Ranges")

        private val BytesValue = ByteString("bytes")

    }

}
