package org.genhttp.engine.ioxide.infrastructure

import ioxide.Reactor

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.shared.hosting.AbstractServerHost
import org.genhttp.engine.shared.infrastructure.ServerConfiguration

/**
 * Server host creating instances of the ioxide engine (`ServerHost` of the ioxide engine in GenHTTP).
 */
internal class IoxideServerHost(
    private val onReactorStart: ((Reactor) -> Unit)?,
    private val options: EngineOptions?
) : AbstractServerHost() {

    override fun build(config: ServerConfiguration, handler: Handler): Server =
        IoxideServer(config, handler, onReactorStart, options ?: EngineOptions())

}
