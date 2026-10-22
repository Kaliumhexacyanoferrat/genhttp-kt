package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.dependencyinjection.RequestScope
import org.genhttp.modules.dependencyinjection.configure

import org.koin.core.Koin

import java.util.UUID

internal class InjectionConcern(override val content: Handler, val koin: Koin) : Concern {

    override suspend fun prepare(server: Server) {
        server.configure(koin)

        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val scope = koin.createScope(UUID.randomUUID().toString(), RequestScope)

        try {
            request.configure(scope)

            return content.handle(request)
        } finally {
            scope.close()
        }
    }

}
