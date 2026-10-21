package org.genhttp.modules.errorhandling.provider

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.errorhandling.ErrorMapper

import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Catches the exceptions of the wrapped handler and converts them into responses.
 * Requests which are not answered by the handler are converted into not found responses.
 *
 * Cancellations of the coroutine handling the request are never converted.
 *
 * @param T The type of the exceptions to be handled
 * @param content The handler wrapped by this concern
 * @param errorHandler The mapper used to convert the exceptions
 * @param errorType The type of the exceptions to be handled (required as generic types are erased on the JVM)
 */
public class ErrorSentry<T : Throwable>(
    override val content: Handler,
    private val errorHandler: ErrorMapper<T>,
    private val errorType: KClass<T>
) : Concern {

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        // the headers of a request with a body must be read before the body is consumed
        val eagerRequired = request.hasBody

        val eagerAccepted = if (eagerRequired) request.header.headers.getEntry(KnownHeaders.Accept) else null

        try {
            val response = content.handle(request)

            if (response == null) {
                return errorHandler.getNotFound(request, content, getAccepted(request, eagerRequired, eagerAccepted))
            }

            return response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!errorType.isInstance(e)) {
                throw e
            }

            return errorHandler.map(request, content, errorType.javaObjectType.cast(e), getAccepted(request, eagerRequired, eagerAccepted))
        }
    }

    private fun getAccepted(request: Request, eagerRequired: Boolean, eagerAccepted: ByteString?): ByteString? {
        if (eagerRequired) {
            return eagerAccepted
        }

        return request.header.headers.getEntry(KnownHeaders.Accept)
    }

}
