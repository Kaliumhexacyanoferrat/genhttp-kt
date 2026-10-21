package org.genhttp.modules.functional

import org.genhttp.modules.functional.provider.InlineBuilder

/**
 * Creates services from lambdas or function references.
 */
public object Inline {

    /**
     * Creates a new, empty inline service.
     */
    public fun create(): InlineBuilder = InlineBuilder()

}
