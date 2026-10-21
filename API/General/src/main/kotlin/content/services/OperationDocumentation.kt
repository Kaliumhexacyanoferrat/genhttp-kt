package org.genhttp.api.content.services

import org.genhttp.api.protocol.RequestMethod

/**
 * Collects the documentation a concern adds to a single operation of the content it wraps (see [DocumentedConcern]).
 *
 * @property method The HTTP verb of the operation to be documented
 */
public class OperationDocumentation(public val method: RequestMethod) {

    private val _entries = mutableListOf<Annotation>()

    /**
     * The documentation added to the operation (annotations marked with [Documentation]).
     */
    public val entries: List<Annotation>
        get() = _entries

    /**
     * Adds the given documentation to the operation.
     *
     * @param documentation The documentation to be added (an annotation marked with [Documentation])
     */
    public fun add(documentation: Annotation): OperationDocumentation {
        require(documentation.isDocumentation) { "Annotation '${documentation.annotationClass.simpleName}' is no documentation annotation" }

        _entries.add(documentation)
        return this
    }

}
