package org.genhttp.modules.io.providers

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Builds a handler from a function.
 */
public class InlineHandlerBuilder : HandlerBuilder.WithConcerns<InlineHandlerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var logic: (suspend (Request) -> Response?)? = null

    /**
     * Sets the logic to be executed by the handler.
     *
     * @param logic The logic to be executed
     */
    public fun logic(logic: suspend (Request) -> Response?): InlineHandlerBuilder {
        this.logic = logic
        return this
    }

    override fun add(concern: ConcernBuilder): InlineHandlerBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val logic = logic ?: throw BuilderMissingPropertyException("Logic")

        return Concerns.chain(concerns, InlineHandler(logic))
    }

}
