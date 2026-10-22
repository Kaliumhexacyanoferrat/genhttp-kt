package org.genhttp.modules.serversentevents.handler

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.serversentevents.EventConnection

import java.nio.ByteBuffer

import kotlin.coroutines.cancellation.CancellationException

/**
 * The response content of an event source: Runs the generator that sends events to the client for as long
 * as the generator is running.
 *
 * @param request The request issued by the client
 * @param lastEventId The ID of the last event received by the client, if any
 * @param generator The function sending the events
 * @param formatters The formatters used to convert values into their string representation
 */
public class EventStream(
    private val request: Request,
    private val lastEventId: String?,
    private val generator: suspend (EventConnection) -> Unit,
    private val formatters: FormatterRegistry
) : ResponseContent {

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = ContentType.TextEventStream

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = null

    override suspend fun write(sink: ResponseSink) {
        val connection = StreamEventConnection(request, lastEventId, sink.stream, formatters)

        try {
            generator(connection)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val logger = request.server.logging.getLogger(EventStream::class.java.name)

            logger.error("Unhandled exception in event stream generator", e)

            connection.retry(ErrorRetryTimeout)
        }
    }

    // endregion

    private companion object {

        private const val ErrorRetryTimeout = 30_000

    }

}
