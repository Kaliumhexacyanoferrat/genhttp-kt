package org.genhttp.modules.layouting

import org.genhttp.modules.layouting.provider.LayoutBuilder

/**
 * Entry point to create layouts that route requests by their path segments.
 */
public object Layout {

    /**
     * Creates a new layout that can be used to route requests.
     *
     * @return The newly created layout builder
     */
    public fun create(): LayoutBuilder = LayoutBuilder()

}
