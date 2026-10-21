package org.genhttp.engine.shared.infrastructure

import org.genhttp.engine.shared.infrastructure.compliance.ComplianceConcernBuilder

/**
 * Factory for the concern that rejects requests violating the HTTP specification.
 */
public object ServerCompliance {

    /**
     * Creates the compliance concern.
     */
    public fun create(): ComplianceConcernBuilder = ComplianceConcernBuilder()

}
