package org.genhttp.api.content

/**
 * Utility class to work with concerns.
 */
public object Concerns {

    /**
     * Embeds the given handler into a handler chain build from the given concerns.
     *
     * @param concerns The concerns the handler should be chained to
     * @param handler The handler to be chained
     * @return The outermost handler of the new chain
     */
    public fun chain(concerns: Iterable<ConcernBuilder>, handler: Handler): Handler {
        var content = handler

        for (concern in concerns) {
            content = concern.build(content)
        }

        return content
    }

}
