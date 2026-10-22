package org.genhttp.modules.reverseproxy.http

import org.genhttp.api.protocol.RequestBody as ApiRequestBody

import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers

/**
 * Streams the body of an incoming request to the upstream server (the counterpart of the `HttpContent` used by
 * GenHTTP, as the JDK HTTP client sends bodies via publishers).
 *
 * @param body The body of the request to be relayed
 * @param contentLength The number of bytes of the body, if known (the upstream server will be addressed
 * with chunked transfer encoding otherwise)
 */
public class RequestBody(body: ApiRequestBody, contentLength: Long = -1) :
    BodyPublisher by createPublisher(body, contentLength)

private fun createPublisher(body: ApiRequestBody, contentLength: Long): BodyPublisher {
    val stream = BodyPublishers.ofInputStream { body.asStream() }

    return when {
        contentLength == 0L -> BodyPublishers.noBody()
        contentLength > 0 -> BodyPublishers.fromPublisher(stream, contentLength)
        else -> stream
    }
}
