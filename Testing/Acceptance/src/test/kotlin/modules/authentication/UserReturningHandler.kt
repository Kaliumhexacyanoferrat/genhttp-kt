package org.genhttp.testing.acceptance.modules.authentication

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.getUser
import org.genhttp.modules.io.strings.StringContent

class UserReturningHandler : Handler {

    override suspend fun prepare(server: Server) {}

    override suspend fun handle(request: Request): Response? {
        val content = request.getUser<User>()?.displayName ?: throw ProviderException(ResponseStatus.BadRequest, "No user!")

        return request.respond()
            .content(StringContent(content))
            .build()
    }

}
