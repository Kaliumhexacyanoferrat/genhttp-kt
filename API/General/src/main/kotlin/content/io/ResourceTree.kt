package org.genhttp.api.content.io

/**
 * Provides resources organized into a tree structure (e.g. a directory or classpath resources).
 */
public interface ResourceTree : ResourceContainer {

    /**
     * Anchor for factory functions provided by modules (e.g. `ResourceTree.fromDirectory("...")`).
     */
    public companion object

}
