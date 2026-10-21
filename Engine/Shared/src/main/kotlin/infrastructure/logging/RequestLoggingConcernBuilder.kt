package org.genhttp.engine.shared.infrastructure.logging

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

import org.slf4j.Logger

import java.util.Locale

/**
 * Builds the request logging concern.
 */
public class RequestLoggingConcernBuilder : ConcernBuilder {

    override fun build(content: Handler): Concern = RequestLoggingConcern(content)

}
