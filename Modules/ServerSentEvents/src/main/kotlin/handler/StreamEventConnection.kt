package org.genhttp.modules.serversentevents.handler

import org.genhttp.api.protocol.Request
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.serversentevents.EventConnection

import java.io.IOException
import java.io.OutputStream

/**
 * Sends events to a client by writing them to a stream (the `EventConnection` of GenHTTP, renamed as the
 * interface has lost its `I` prefix).
 *
 * @param request The request issued by the client to establish the connection
 * @param lastEventId The ID of the last event received by the client, if any
 * @param target The stream to write the events to
 * @param formatters The formatters used to convert values into their string representation
 */
public class StreamEventConnection(
    override val request: Request,
    override val lastEventId: String?,
    private val target: OutputStream,
    private val formatters: FormatterRegistry
) : EventConnection {

    // region Get-/Setters

    override var connected: Boolean = true
        private set

    // endregion

    // region Functionality

    override suspend fun comment(comment: String): Boolean = send(": $comment$NL$NL")

    override suspend fun data(data: String, eventType: String?, eventId: String?): Boolean {
        if (eventType != null) {
            send("event: $eventType$NL")
        }

        if (eventId != null) {
            send("id: $eventId$NL")
        }

        return send("data: $data$NL$NL")
    }

    override suspend fun <T> data(data: T, eventType: String?, eventId: String?): Boolean {
        if (data == null) {
            return data("", eventType, eventId)
        }

        val type = data::class

        if (formatters.canHandle(type)) {
            return data(formatters.write(data, type) ?: "", eventType, eventId)
        }

        return data(SerializationMapper.writeValueAsString(data), eventType, eventId)
    }

    override suspend fun retry(milliseconds: Int): Boolean {
        if (send("retry: $milliseconds$NL$NL")) {
            connected = false
            return true
        }

        return false
    }

    private fun send(message: String): Boolean {
        if (!connected) return false

        try {
            target.write(message.toByteArray(Charsets.UTF_8))

            target.flush()

            return true
        } catch (_: IOException) {
            // includes ConnectionClosedException
            connected = false
            return false
        }
    }

    // endregion

    private companion object {

        private const val NL = "\n"

        // camel case names, null values are not written, no indentation
        private val SerializationMapper = JsonFormat().mapper

    }

}
