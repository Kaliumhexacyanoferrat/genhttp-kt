package org.genhttp.modules.inspection

import org.genhttp.modules.inspection.concern.InspectionConcernBuilder

/**
 * Entry point to create concerns that allow to inspect the requests and responses handled by the server.
 */
public object Inspector {

    // region Builder

    /**
     * Creates a new concern that serializes information about the server, the request and the response, if the
     * `inspect` query parameter has been added to the request.
     *
     * @return The newly created inspection concern builder
     */
    public fun create(): InspectionConcernBuilder = InspectionConcernBuilder()

    // endregion

}
