package org.genhttp.modules.serversentevents.handler

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.serversentevents.EventConnection

/**
 * Handles the requests of clients that subscribe to a stream of events.
 *
 * @param inspector Allows to decline the connection of a client (receives the request and the ID of the last received event)
 * @param generator The function sending the events to the client
 * @param formatters The formatters used to convert values into their string representation
 */
public class EventSourceHandler(
    private val inspector: (suspend (Request, String?) -> Boolean)?,
    private val generator: suspend (EventConnection) -> Unit,
    private val formatters: FormatterRegistry
) : Handler {

    // region Functionality

    override suspend fun prepare(server: Server) {
        // nothing to prepare
    }

    override suspend fun handle(request: Request): Response? {
        if (request.header.method != RequestMethod.Get) {
            throw ProviderException(ResponseStatus.MethodNotAllowed, "Server Sent Events require a GET request to establish a connection") {
                header("Allow", "GET")
            }
        }

        val lastId = request.header.headers.getEntry("Last-Event-ID")

        if (inspector != null && !inspector(request, lastId)) {
            return request.respond()
                .status(ResponseStatus.NoContent)
                .build()
        }

        val content = EventStream(request, lastId, generator, formatters)

        return request.respond()
            .status(ResponseStatus.Ok)
            .content(content)
            .build()
    }

    // endregion

}
