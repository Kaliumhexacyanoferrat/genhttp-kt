package org.genhttp.engine.shared.infrastructure.compliance

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.containsKey
import org.genhttp.api.protocol.getEntry

/**
 * Builds the compliance concern.
 */
public class ComplianceConcernBuilder : ConcernBuilder {

    override fun build(content: Handler): Concern = ComplianceConcern(content)

}
