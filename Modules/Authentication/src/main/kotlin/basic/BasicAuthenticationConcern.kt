package org.genhttp.modules.authentication.basic

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.setUser

import java.util.Base64

/**
 * Denies access to the wrapped content unless the client passes valid credentials via basic authentication.
 *
 * @param content The handler to be wrapped by this concern
 * @param realm The name of the realm returned to the client
 * @param authenticator The logic used to check the credentials and to determine the user (receives the user name and the password)
 */
public class BasicAuthenticationConcern(
    override val content: Handler,
    private val realm: String,
    private val authenticator: suspend (String, String) -> User?
) : Concern {

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val authHeader = request.header.headers.getEntry("Authorization")
            ?: return getChallenge(request)

        if (!authHeader.startsWith("Basic ")) {
            return getChallenge(request)
        }

        val credentials = tryDecode(authHeader.substring(6))
            ?: return getChallenge(request)

        val user = authenticator(credentials.first, credentials.second)
            ?: return getChallenge(request)

        request.setUser(user)

        return content.handle(request)
    }

    private fun getChallenge(request: Request): Response = request.respond()
        .status(ResponseStatus.Unauthorized)
        .header("WWW-Authenticate", "Basic realm=\"$realm\", charset=\"UTF-8\"")
        .build()

    private fun tryDecode(header: String): Pair<String, String>? {
        try {
            val bytes = Base64.getDecoder().decode(normalize(header))

            val str = String(bytes, Charsets.UTF_8)

            val colon = str.indexOf(':')

            if (colon > -1 && str.length > colon) {
                return str.substring(0, colon) to str.substring(colon + 1)
            }
        } catch (_: IllegalArgumentException) {
            // invalid base 64 encoded string
        }

        return null
    }

    /**
     * Mimics `Convert.FromBase64String`: white space is ignored, but the padding is required.
     */
    private fun normalize(value: String): String {
        val result = value.filter { it != ' ' && it != '\t' && it != '\r' && it != '\n' }

        require(result.length % 4 == 0) { "Invalid length for a Base-64 string" }

        return result
    }

    // endregion

}
