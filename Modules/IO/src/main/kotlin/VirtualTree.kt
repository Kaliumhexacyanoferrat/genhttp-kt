package org.genhttp.modules.io

import org.genhttp.modules.io.virtualtrees.VirtualTreeBuilder

/**
 * Entry point to create virtual trees.
 */
public object VirtualTree {

    /**
     * Creates a virtual tree that may contain any other kind
     * of tree or resource and allows to combine them.
     */
    public fun create(): VirtualTreeBuilder = VirtualTreeBuilder()

}
