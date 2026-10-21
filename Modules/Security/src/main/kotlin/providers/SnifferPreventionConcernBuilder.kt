package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler

/**
 * Builds a concern that prevents browsers from guessing the content type of responses.
 */
public class SnifferPreventionConcernBuilder : ConcernBuilder {

    override fun build(content: Handler): Concern = SnifferPreventionConcern(content)

}
