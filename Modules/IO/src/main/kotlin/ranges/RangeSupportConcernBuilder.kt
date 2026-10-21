package org.genhttp.modules.io.ranges

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler

/**
 * Creates the concern that adds support for partial responses (HTTP range requests).
 */
public class RangeSupportConcernBuilder : ConcernBuilder {

    override fun build(content: Handler): Concern = RangeSupportConcern(content)

}
