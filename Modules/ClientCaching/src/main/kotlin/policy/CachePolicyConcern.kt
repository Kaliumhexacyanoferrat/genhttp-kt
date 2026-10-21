package org.genhttp.modules.clientcaching.policy

import org.genhttp.api.content.Handler
import org.genhttp.api.content.services.ApiResponseHeader
import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.hasType

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Adds an `Expires` header to successful GET responses, allowing the client to cache them.
 *
 * @param content The handler to be wrapped by this concern
 * @param duration The duration the content should be cached on the client
 * @param predicate Optional predicate to be evaluated to check, whether content should be cached
 */
public class CachePolicyConcern(
    override val content: Handler,
    private val duration: Duration,
    private val predicate: ((Request, Response) -> Boolean)?
) : DocumentedConcern {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val response = content.handle(request)

        if (response != null) {
            if (request.hasType(RequestMethod.Get) && response.status == ResponseStatus.Ok) {
                if (predicate == null || predicate(request, response)) {
                    val value = Instant.now().plus(duration.toJavaDuration())

                    response.rebuild()
                        .header(KnownHeaders.Expires, ByteString(Rfc1123.format(value)))
                }
            }
        }

        return response
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        if (operation.method == RequestMethod.Get) {
            operation.add(ApiResponseHeader(name = "Expires", description = "Until when the client may cache the response", status = arrayOf(ResponseStatus.Ok)))
        }
    }

    // endregion

    private companion object {

        private val Rfc1123: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)

    }

}
