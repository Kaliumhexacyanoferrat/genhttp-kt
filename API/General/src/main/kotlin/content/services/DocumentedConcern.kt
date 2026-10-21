package org.genhttp.api.content.services

import org.genhttp.api.content.Concern

/**
 * A concern that documents the functionality it adds to the content it wraps (such as the headers it reads or the
 * responses it may generate), so it can be presented to the consumers of the API.
 *
 * The documentation added by the concern is treated as if it was declared on the service class of the operation.
 * Documentation declared on the services themselves and documentation of concerns closer to the content take
 * precedence.
 */
public interface DocumentedConcern : Concern {

    /**
     * Invoked for every operation provided by the wrapped content, allowing the concern to document the
     * functionality it adds to this specific operation.
     *
     * @param operation The operation to add the documentation to
     */
    public fun addDocumentation(operation: OperationDocumentation)

}
