package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getEntry

/**
 * Instructs clients to only use secured connections by adding the `Strict-Transport-Security` header (HSTS).
 *
 * @param content The handler to be wrapped by this concern
 * @param policy The policy to be announced to the clients
 */
public class StrictTransportConcern(override val content: Handler, policy: StrictTransportPolicy) : Concern {

    // region Get-/Setters

    /**
     * The policy announced to the clients.
     */
    public val policy: StrictTransportPolicy = policy

    private val headerValue: ByteString = getPolicyHeader()

    // endregion

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val response = content.handle(request)

        if (response != null) {
            if (request.endPoint.secure) {
                val existing = response.headers.getEntry(StrictTransportHeader)

                if (existing == null) {
                    response.rebuild().header(StrictTransportHeader, headerValue)
                }
            }
        }

        return response
    }

    private fun getPolicyHeader(): ByteString {
        val seconds = policy.maximumAge.inWholeSeconds.toInt()

        var result = "max-age=$seconds"

        if (policy.includeSubdomains) {
            result += "; includeSubDomains"
        }

        if (policy.preload) {
            result += "; preload"
        }

        return ByteString(result)
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    // endregion

    private companion object {

        private val StrictTransportHeader = ByteString("Strict-Transport-Security")

    }

}
