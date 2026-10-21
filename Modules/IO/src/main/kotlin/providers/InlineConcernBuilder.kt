package org.genhttp.modules.io.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Builds a concern from a function.
 */
public class InlineConcernBuilder : ConcernBuilder {

    private var logic: (suspend (Request, Handler) -> Response?)? = null

    /**
     * Sets the logic to be executed by the concern.
     *
     * @param logic The logic to be executed
     */
    public fun logic(logic: suspend (Request, Handler) -> Response?): InlineConcernBuilder {
        this.logic = logic
        return this
    }

    override fun build(content: Handler): Concern {
        val logic = logic ?: throw BuilderMissingPropertyException("Logic")

        return InlineConcern(content, logic)
    }

}
