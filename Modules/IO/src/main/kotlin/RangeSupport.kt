package org.genhttp.modules.io

import org.genhttp.modules.io.ranges.RangeSupportConcernBuilder

/**
 * Entry point to add support for partial responses (HTTP range requests).
 */
public object RangeSupport {

    /**
     * Creates the concern that adds support for partial responses to the content it is added to.
     */
    public fun create(): RangeSupportConcernBuilder = RangeSupportConcernBuilder()

}
