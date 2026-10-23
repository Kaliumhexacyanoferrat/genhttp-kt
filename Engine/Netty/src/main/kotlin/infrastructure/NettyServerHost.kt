package org.genhttp.engine.netty.infrastructure

import io.netty.channel.EventLoop

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.shared.hosting.AbstractServerHost
import org.genhttp.engine.shared.infrastructure.ServerConfiguration

/**
 * Server host creating instances of the Netty engine (`ServerHost` of the ioxide engine in GenHTTP).
 */
internal class NettyServerHost(
    private val onEventLoopStart: ((EventLoop) -> Unit)?,
    private val options: EngineOptions?
) : AbstractServerHost() {

    override fun build(config: ServerConfiguration, handler: Handler): Server =
        NettyServer(config, handler, onEventLoopStart, options ?: EngineOptions())

}
