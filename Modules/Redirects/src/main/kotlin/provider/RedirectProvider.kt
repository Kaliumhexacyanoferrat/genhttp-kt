package org.genhttp.modules.redirects.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.io.hasType

import java.net.URI

/**
 * A handler that redirects the requesting client to another location.
 *
 * @param location The location to redirect the client to
 * @param temporary `false`, if the client should remember this redirection
 */
public class RedirectProvider(location: URI, temporary: Boolean) : Handler {

    // region Get-/Setters

    /**
     * The location the client will be redirected to.
     */
    public val target: URI = location

    // relative locations are rendered unescaped, like Uri.ToString() does in .NET
    private val stringTarget: String = if (location.isAbsolute) location.toString() else
        location.schemeSpecificPart + (location.fragment?.let { "#$it" } ?: "")

    /**
     * Specifies whether the redirection is temporary or whether the client should remember it.
     */
    public val temporary: Boolean = temporary

    // endregion

    // region Functionality

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val resolved = resolveRoute(request)

        val status = mapStatus(request, temporary)

        val response = request.respond()
            .header("Location", resolved)
            .status(status)

        return response.build()
    }

    private fun resolveRoute(request: Request): String {
        if (target.isAbsolute) {
            return stringTarget
        }

        val protocol = if (request.endPoint.secure) "https://" else "http://"

        val host = request.header.headers.getEntry("Host") ?: ""

        return "$protocol$host$stringTarget"
    }

    private fun mapStatus(request: Request, temporary: Boolean): ResponseStatus {
        if (request.hasType(RequestMethod.Get, RequestMethod.Head)) {
            return if (temporary) ResponseStatus.TemporaryRedirect else ResponseStatus.MovedPermanently
        }

        return if (temporary) ResponseStatus.SeeOther else ResponseStatus.PermanentRedirect
    }

    // endregion

}
