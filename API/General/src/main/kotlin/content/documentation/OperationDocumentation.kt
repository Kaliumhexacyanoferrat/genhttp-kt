package org.genhttp.api.content.documentation

import org.genhttp.api.content.documentation.security.AuthenticationRequirement
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus

import kotlin.reflect.KClass

/**
 * Collects the documentation a component (such as a concern) adds to a single operation of the content it is
 * applied to (see [DocumentedConcern]).
 *
 * @property method The HTTP verb of the operation to be documented
 */
public class OperationDocumentation(public val method: RequestMethod) {

    private val _entries = mutableListOf<Annotation>()

    private val _authentication = mutableListOf<AuthenticationRequirement>()

    // region Get-/Setters

    /**
     * The responses and headers added to the operation (annotations marked with [Documentation]).
     */
    public val entries: List<Annotation>
        get() = _entries

    /**
     * The authentication required to access the operation. All of the requirements need to be satisfied by the
     * client.
     */
    public val authentication: List<AuthenticationRequirement>
        get() = _authentication

    // endregion

    // region Functionality

    /**
     * Declares a response that may be generated for the operation.
     *
     * See [ApiResponse] for details.
     *
     * @param status The status of the response
     * @param description Describes the circumstances the response is generated in
     * @param type The type of the content sent with the response, if any
     * @param contentType The content type of the response (e.g. "text/plain"), if known
     */
    public fun response(status: ResponseStatus, description: String? = null, type: KClass<*>? = null, contentType: String? = null): OperationDocumentation {
        _entries.add(ApiResponse(status, description ?: "", type ?: Nothing::class, contentType ?: ""))
        return this
    }

    /**
     * Declares a header that is read from the request.
     *
     * @param name The name of the header (e.g. "X-Request-Id")
     * @param description Describes the value expected to be sent by the client
     * @param required Whether the client is required to send this header
     * @param type The type of the value of the header, if it is not a plain string
     */
    public fun requestHeader(name: String, description: String? = null, required: Boolean = false, type: KClass<*>? = null): OperationDocumentation {
        _entries.add(ApiRequestHeader(name, description ?: "", required, type ?: Nothing::class))
        return this
    }

    /**
     * Declares a header that is sent with the response.
     *
     * See [ApiResponseHeader] for details.
     *
     * @param name The name of the header (e.g. "Retry-After")
     * @param description Describes the value sent with the header
     * @param status The status of the response the header is sent with, or null for every successful response
     * @param type The type of the value of the header, if it is not a plain string
     */
    public fun responseHeader(name: String, description: String? = null, status: ResponseStatus? = null, type: KClass<*>? = null): OperationDocumentation {
        val statuses = if (status != null) arrayOf(status) else emptyArray()

        _entries.add(ApiResponseHeader(name, description ?: "", statuses, type ?: Nothing::class))
        return this
    }

    /**
     * Declares that the client needs to authenticate with any one of the given schemes to access the operation.
     *
     * If called multiple times, all of the requirements need to be satisfied.
     *
     * @param anyOf The schemes the client may choose from
     */
    public fun requireAuthentication(vararg anyOf: SecurityScheme): OperationDocumentation {
        require(anyOf.isNotEmpty()) { "At least one security scheme is required" }

        _authentication.add(AuthenticationRequirement(anyOf.toList()))
        return this
    }

    // endregion

}
