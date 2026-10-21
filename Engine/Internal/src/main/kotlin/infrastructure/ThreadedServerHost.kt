package org.genhttp.engine.internal.infrastructure

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.shared.hosting.AbstractServerHost
import org.genhttp.engine.shared.infrastructure.ServerConfiguration

/**
 * Server host creating instances of the internal engine.
 */
internal class ThreadedServerHost : AbstractServerHost() {

    override fun build(config: ServerConfiguration, handler: Handler): Server = ThreadedServer(config, handler)

}
