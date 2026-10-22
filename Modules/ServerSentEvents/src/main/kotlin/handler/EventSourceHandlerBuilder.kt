package org.genhttp.modules.serversentevents.handler

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.formatters.FormatterBuilder
import org.genhttp.modules.serversentevents.EventConnection

/**
 * Configures a handler that sends events to the connected clients.
 */
public class EventSourceHandlerBuilder : HandlerBuilder.WithConcerns<EventSourceHandlerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var formatters: FormatterBuilder = Formatting.default()

    private var inspector: (suspend (Request, String?) -> Boolean)? = null

    private var generator: (suspend (EventConnection) -> Unit)? = null

    // region Functionality

    /**
     * Sets a function that is invoked before a client is connected and that can decline the connection
     * (the client will receive a response without content and will not attempt to reconnect).
     *
     * @param inspector The function receiving the request and the ID of the last event received by the client; returns true to accept the connection
     */
    public fun inspector(inspector: suspend (Request, String?) -> Boolean): EventSourceHandlerBuilder {
        this.inspector = inspector
        return this
    }

    /**
     * Sets the function that is generating the events. The connection with the client will be kept open
     * until the function returns.
     *
     * @param generator The function sending the events via the given connection
     */
    public fun generator(generator: suspend (EventConnection) -> Unit): EventSourceHandlerBuilder {
        this.generator = generator
        return this
    }

    /**
     * Sets the formatters used to convert values sent via [EventConnection.data] into strings.
     *
     * @param formatters The formatters to be used
     */
    public fun formatting(formatters: FormatterBuilder): EventSourceHandlerBuilder {
        this.formatters = formatters
        return this
    }

    override fun add(concern: ConcernBuilder): EventSourceHandlerBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val generator = generator ?: throw BuilderMissingPropertyException("Generator")

        return Concerns.chain(concerns, EventSourceHandler(inspector, generator, formatters.build()))
    }

    // endregion

}
