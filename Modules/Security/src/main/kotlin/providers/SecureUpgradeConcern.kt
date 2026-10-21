package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.RequestQuery
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.modules.io.getHostWithoutPort
import org.genhttp.modules.redirects.Redirect
import org.genhttp.modules.security.SecureUpgrade

/**
 * Redirects requests to insecure endpoints to the secured endpoints of the server.
 *
 * @param content The handler to be wrapped by this concern
 * @param mode The strategy to be used to upgrade requests
 */
public class SecureUpgradeConcern(override val content: Handler, mode: SecureUpgrade) : Concern {

    // region Get-/Setters

    /**
     * The strategy used to upgrade requests.
     */
    public val mode: SecureUpgrade = mode

    // endregion

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        if (!request.endPoint.secure) {
            if (request.server.endPoints.any { it.secure }) {
                val endpoints = request.server.endPoints.filter { it.secure }

                if (endpoints.isNotEmpty()) {
                    if (mode == SecureUpgrade.Force && !isAcmeChallenge(request)) {
                        return Redirect.to(getRedirectLocation(request, endpoints))
                            .build()
                            .handle(request)
                    }

                    if (mode == SecureUpgrade.Allow) {
                        if (request.header.method == RequestMethod.Get) {
                            val flag = request.header.headers.getEntry(UpgradeInsecureRequestsHeader)

                            if (flag == YesValue) {
                                val response = Redirect.to(getRedirectLocation(request, endpoints), true)
                                    .build()
                                    .handle(request)

                                response?.rebuild()?.header(VaryHeader, UpgradeInsecureRequestsHeader)

                                return response
                            }
                        }
                    }
                }
            }
        }

        return content.handle(request)
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    // endregion

    private companion object {

        private val UpgradeInsecureRequestsHeader = ByteString("Upgrade-Insecure-Requests")

        private val VaryHeader = ByteString("Vary")

        private val YesValue = ByteString("1")

        private const val AcmeChallengePrefix = "/.well-known/acme-challenge/"

        private fun getRedirectLocation(request: Request, endPoints: List<EndPoint>): String {
            val targetPort = getTargetPort(request, endPoints)

            val port = if (targetPort == 443) "" else ":$targetPort"

            val host = request.getHostWithoutPort()

            val hostString = host?.toString() ?: ""

            val location = StringBuilder()

            location.append("https://")
                .append(hostString)
                .append(port)
                .append(request.header.path.toString())

            appendQuery(location, request.header.query)

            return location.toString()
        }

        private fun appendQuery(location: StringBuilder, query: RequestQuery) {
            for (i in 0 until query.count) {
                val entry = query.getStringEntry(i)

                location.append(if (i == 0) '?' else '&')
                    .append(entry.first.toString())

                if (!entry.second.isEmpty) {
                    location.append('=')
                        .append(entry.second.toString())
                }
            }
        }

        private fun isAcmeChallenge(request: Request): Boolean =
            request.header.method == RequestMethod.Get && request.header.path.toString().startsWith(AcmeChallengePrefix)

        private fun getTargetPort(request: Request, endPoints: List<EndPoint>): Int {
            // this extension can only be added if there are secure endpoints available
            if (endPoints.isEmpty()) {
                throw UnsupportedOperationException("No secure endpoints available")
            }

            // if there is a correlated port, use this one
            val correlated = (request.endPoint.port + (443 - 80)) and 0xFFFF

            if (endPoints.any { it.port == correlated }) {
                return correlated
            }

            // default to 443, if available
            if (endPoints.any { it.port == 443 }) {
                return 443
            }

            // use the first secure endpoint
            return endPoints.first().port
        }

    }

}
