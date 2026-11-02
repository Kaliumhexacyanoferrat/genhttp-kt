package org.genhttp.modules.openapi.handler

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server as HttpServer
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistry
import org.genhttp.modules.openapi.discovery.InheritedDocumentation
import org.genhttp.modules.openapi.discovery.SchemaManager

internal data class ReturnDocument(val document: OpenAPI, val checksum: Long)

/**
 * A concern that analyzes the wrapped content and serves the generated OpenAPI specification.
 *
 * @param content The handler to be analyzed
 * @param discovery The registry of the explorers used to analyze the content
 * @param enableCaching Whether the specification should be generated only once
 * @param postProcessor Invoked after the document has been generated to adjust it
 */
public class OpenApiConcern(
    override val content: Handler,
    private val discovery: ApiDiscoveryRegistry,
    private val enableCaching: Boolean,
    private val postProcessor: (Request, OpenAPI) -> Unit
) : Concern {

    @Volatile
    private var cached: ReturnDocument? = null

    // region Functionality

    override suspend fun prepare(server: HttpServer) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        if (request.header.method == RequestMethod.Get || request.header.method == RequestMethod.Head) {
            val path = request.header.target.current

            if (path != null) {
                if (path == OpenApiPath) {
                    val accept = request.header.headers.getEntry(KnownHeaders.Accept)

                    val format = if (accept != null) getFormat(ContentType(accept.bytes)) else OpenApiFormat.Json

                    if (format == null) {
                        throw ProviderException(ResponseStatus.BadRequest, "Generating API specifications of format '$accept' is not supported")
                    }

                    val response = getDocument(request, format)

                    response.rebuild().header(KnownHeaders.Vary, KnownHeaders.Accept)

                    return response
                }

                if (path == OpenApiJsonPath) {
                    return getDocument(request, OpenApiFormat.Json)
                }

                if (path == OpenApiYamlPath || path == OpenApiYmlPath) {
                    return getDocument(request, OpenApiFormat.Yaml)
                }
            }
        }

        return content.handle(request)
    }

    private fun getFormat(accept: ContentType): OpenApiFormat? {
        if (accept == ContentType.ApplicationJson || accept == ContentType.ApplicationOpenApiJson) {
            return OpenApiFormat.Json
        }

        if (accept == ContentType.ApplicationYaml || accept == ContentType.ApplicationOpenApiYaml) {
            return OpenApiFormat.Yaml
        }

        return null
    }

    private suspend fun getDocument(request: Request, format: OpenApiFormat): Response {
        val document = discover(request, discovery)

        return request.respond()
            .content(OpenApiContent(document, format))
            .build()
    }

    private suspend fun discover(request: Request, registry: ApiDiscoveryRegistry): ReturnDocument {
        if (enableCaching) {
            cached?.let { return it }
        }

        // NSwag creates documents with these defaults
        val document = OpenAPI().apply {
            openapi = "3.0.0"
            info = Info().title("Swagger specification").version("1.0.0")
        }

        val schemata = SchemaManager(document)

        val path = request.header.target.asString(decode = false)

        val host = request.header.headers.getEntry("Host")

        if (host != null) {
            document.addServersItem(
                Server().url((if (request.endPoint.secure) "https://" else "http://") + host + path.substring(0, path.lastIndexOf('/')))
            )
        }

        registry.explore(request, content, emptyList(), document, schemata, InheritedDocumentation.Empty)

        postProcessor.invoke(request, document)

        val result = ReturnDocument(document, getChecksum(document))

        if (enableCaching) {
            cached = result
        }

        return result
    }

    private fun getChecksum(document: OpenAPI): Long = Json.pretty(document).hashCode().toLong()

    // endregion

    private companion object {

        val OpenApiPath = PathSegment("openapi")

        val OpenApiJsonPath = PathSegment("openapi.json")

        val OpenApiYamlPath = PathSegment("openapi.yaml")

        val OpenApiYmlPath = PathSegment("openapi.yml")

    }

}
