package org.genhttp.modules.security.cors

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.preAllocate
import org.genhttp.modules.io.hasType

/**
 * Applies a cross-origin resource sharing (CORS) policy to the responses of the handler it wraps
 * and answers preflight requests.
 *
 * @param content The handler to be wrapped by this concern
 * @param defaultPolicy The policy to be applied, if no origin is given or no specific policy exists for it
 * @param additionalPolicies The policies to be applied for specific origins
 */
public class CorsPolicyHandler(
    override val content: Handler,
    defaultPolicy: OriginPolicy?,
    additionalPolicies: Map<String, OriginPolicy?>
) : Concern {

    // region Get-/Setters

    /**
     * The policy to be applied, if no origin is given or no specific policy exists for it.
     */
    public val defaultPolicy: OriginPolicy? = defaultPolicy

    /**
     * The policies to be applied for specific origins.
     */
    public val additionalPolicies: Map<String, OriginPolicy?> = additionalPolicies

    // endregion

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val (origin, policy) = getPolicy(request)

        val response: Response? = if (request.hasType(RequestMethod.Options)) {
            request.respond()
                .status(ResponseStatus.NoContent)
                .build()
        } else {
            content.handle(request)
        }

        if (response != null && policy != null) {
            configureResponse(response, origin, policy)
        }

        return response
    }

    private fun getPolicy(request: Request): Pair<ByteString, OriginPolicy?> {
        val origin = request.header.headers.getEntry(OriginHeader).preAllocate(request)

        if (origin != null) {
            val originString = origin.toString()

            if (additionalPolicies.containsKey(originString)) {
                return origin to additionalPolicies[originString]
            }
        }

        return (origin ?: AllowAny) to defaultPolicy
    }

    // endregion

    public companion object {

        private val OriginHeader = ByteString("Origin")

        private val AcaOrigin = ByteString("Access-Control-Allow-Origin")

        private val AcaMethods = ByteString("Access-Control-Allow-Methods")

        private val AcaHeaders = ByteString("Access-Control-Allow-Headers")

        private val AcExposeHeaders = ByteString("Access-Control-Expose-Headers")

        private val AcaCredentials = ByteString("Access-Control-Allow-Credentials")

        private val AcMaxAge = ByteString("Access-Control-Max-Age")

        private val TrueValue = ByteString("true")

        private val VaryHeader = ByteString("Vary")

        /**
         * The wildcard value allowing any origin, method or header.
         */
        public val AllowAny: ByteString = ByteString("*")

        private fun configureResponse(response: Response, origin: ByteString, policy: OriginPolicy) {
            val builder = response.rebuild()

            builder.header(AcaOrigin, origin)

            if (hasValue(policy.allowedMethods)) {
                builder.header(AcaMethods, getListOrWildcard(policy.allowedMethods))
            }

            if (hasValue(policy.allowedHeaders)) {
                builder.header(AcaHeaders, getListOrWildcard(policy.allowedHeaders))
            }

            if (hasValue(policy.exposedHeaders)) {
                builder.header(AcExposeHeaders, getListOrWildcard(policy.exposedHeaders))
            }

            if (policy.allowCredentials) {
                builder.header(AcaCredentials, TrueValue)
            }

            builder.header(AcMaxAge, ByteString(policy.maxAge.toString()))

            if (origin != AllowAny) {
                builder.header(VaryHeader, OriginHeader)
            }
        }

        private fun getListOrWildcard(values: List<*>?): ByteString {
            if (values == null) return AllowAny

            return ByteString(values.joinToString(", "))
        }

        private fun hasValue(list: List<*>?): Boolean = list == null || list.isNotEmpty()

    }

}
