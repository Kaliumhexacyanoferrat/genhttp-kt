package org.genhttp.modules.serversentevents

import org.genhttp.api.protocol.Request

/**
 * A connection to a client that has subscribed to a stream of events. All functions return `false`
 * if the event could not be sent because the client is not connected anymore.
 */
public interface EventConnection {

    /**
     * The request issued by the client to establish the connection.
     */
    public val request: Request

    /**
     * The ID of the last event received by the client (sent with the `Last-Event-ID` header), if any.
     */
    public val lastEventId: String?

    /**
     * Whether the client is still connected.
     */
    public val connected: Boolean

    /**
     * Sends a comment to the client.
     *
     * @param comment The comment to be sent
     * @return true, if the comment has been sent
     */
    public suspend fun comment(comment: String): Boolean

    /**
     * Sends the given data to the client.
     *
     * @param data The data to be sent
     * @param eventType The type of the event (sent with the `event` field), if any
     * @param eventId The ID of the event (sent with the `id` field), if any
     * @return true, if the event has been sent
     */
    public suspend fun data(data: String, eventType: String? = null, eventId: String? = null): Boolean

    /**
     * Sends the given value to the client. The value will be formatted via the formatters registered for the
     * handler. If there is no formatter for the type of the value, it will be serialized to JSON.
     *
     * @param data The value to be sent
     * @param eventType The type of the event (sent with the `event` field), if any
     * @param eventId The ID of the event (sent with the `id` field), if any
     * @return true, if the event has been sent
     */
    public suspend fun <T> data(data: T, eventType: String? = null, eventId: String? = null): Boolean

    /**
     * Instructs the client to wait for the given amount of time before it attempts to reconnect.
     *
     * @param milliseconds The time to wait in milliseconds
     * @return true, if the instruction has been sent
     */
    public suspend fun retry(milliseconds: Int): Boolean

}
