package org.genhttp.modules.openapi.handler

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.documentation.security.AuthenticationRequirement
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistry
import org.genhttp.modules.openapi.discovery.SecurityDocumentation

/**
 * Creates the concern that generates the OpenAPI specification.
 *
 * @param registry The registry of the explorers used to analyze the handler chain
 */
public class OpenApiConcernBuilder(private val registry: ApiDiscoveryRegistry) : ConcernBuilder {

    private val security = mutableListOf<AuthenticationRequirement>()

    private var caching = true

    private var postProcessor: ((Request, OpenAPI) -> Unit)? = null

    private var title: String? = null

    private var version: String? = null

    // region Functionality

    /**
     * Sets the title of the OpenAPI specification.
     *
     * @param title The title of the API
     */
    public fun title(title: String): OpenApiConcernBuilder {
        this.title = title
        return this
    }

    /**
     * Sets the version of the described API.
     *
     * @param version The version of the API
     */
    public fun version(version: String): OpenApiConcernBuilder {
        this.version = version
        return this
    }

    /**
     * Specifies, whether the generated OpenAPI specification should
     * get cached on first request, so it is no re-generated on every request.
     *
     * @param enabled Whether to use caching or not
     */
    public fun caching(enabled: Boolean): OpenApiConcernBuilder {
        caching = enabled
        return this
    }

    /**
     * Declares that the client needs to authenticate with any one of the given
     * schemes to access the operations of the API.
     *
     * Use this method to document authentication that cannot be discovered from
     * the handler chain (e.g. because it is performed by an API gateway or because
     * the specification itself is served behind an authentication concern). This
     * does not enforce authentication. If called multiple times, all of the
     * requirements need to be satisfied. Operations secured by a concern
     * need to satisfy both the requirements declared here and by the concern.
     *
     * @param anyOf The schemes the client may choose from
     */
    public fun security(vararg anyOf: SecurityScheme): OpenApiConcernBuilder {
        require(anyOf.isNotEmpty()) { "At least one security scheme is required" }

        security.add(AuthenticationRequirement(anyOf.toList()))
        return this
    }

    /**
     * Registers a function that will be called when an OpenAPI document has been
     * generated, directly before it is served to the client.
     *
     * @param action The method to be invoked to adjust the generated document
     */
    public fun postProcessor(action: (Request, OpenAPI) -> Unit): OpenApiConcernBuilder {
        postProcessor = action
        return this
    }

    override fun build(content: Handler): Concern = OpenApiConcern(content, registry, caching, ::doPostProcessing)

    private fun doPostProcessing(request: Request, document: OpenAPI) {
        if (security.isNotEmpty()) {
            applySecurity(document)
        }

        title?.let { document.info.title = it }

        version?.let { document.info.version = it }

        postProcessor?.invoke(request, document)
    }

    private fun applySecurity(document: OpenAPI) {
        val global = SecurityDocumentation.getRequirements(document, security)

        document.security = global

        // the security of an operation replaces the global one, so both need to be stated
        for (operation in document.paths?.values.orEmpty().flatMap { it.readOperations() }) {
            val operationSecurity = operation.security

            if (!operationSecurity.isNullOrEmpty()) {
                operation.security = SecurityDocumentation.combine(global, operationSecurity)
            }
        }
    }

    // endregion

}
