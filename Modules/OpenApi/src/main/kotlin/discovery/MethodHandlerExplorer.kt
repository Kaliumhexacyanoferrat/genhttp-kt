package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.PathParameter
import io.swagger.v3.oas.models.parameters.QueryParameter
import io.swagger.v3.oas.models.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.models.responses.ApiResponse as OpenApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.tags.Tag as OpenApiTag
import io.swagger.v3.oas.models.Operation as OpenApiOperation

import org.genhttp.api.content.Handler
import org.genhttp.api.content.services.ApiRequestBody
import org.genhttp.api.content.services.ApiRequestHeader
import org.genhttp.api.content.services.ApiResponse
import org.genhttp.api.content.services.ApiResponseHeader
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.content.services.Tag
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.openapi.ApiExplorer
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ResultWrapper
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.modules.reflection.operations.OperationArgumentSource
import org.genhttp.modules.reflection.operations.OperationResultSink

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod

/**
 * Describes the operations of a service (functional handlers, webservices, controllers).
 */
public class MethodHandlerExplorer : ApiExplorer {

    /**
     * Describes the content of a request or response body.
     *
     * @param schema The schema of the content, if known
     * @param mediaTypes The media types the content can be represented with
     */
    private class BodyDescription(val schema: Schema<*>?, val mediaTypes: List<String>)

    override fun canExplore(handler: Handler): Boolean = handler is MethodHandler

    override suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    ) {
        if (handler is MethodHandler) {
            val source = handler.operation

            val tag = getTag(source)

            if (tag != null) {
                addTag(document, tag)
            }

            val pathItem = getPathItem(document, path, source)

            val method = source.method

            for (requestMethod in source.configuration.supportedMethods) {
                if (requestMethod == RequestMethod.Head && source.configuration.supportedMethods.size > 1) {
                    continue
                }

                // OpenAPI cannot describe other verbs (e.g. CONNECT)
                val verb = getVerb(requestMethod) ?: continue

                val inherited = documentation.getDocumentation(requestMethod)

                val operation = OpenApiOperation()

                operation.summary = Documentation.getSummary(method)
                operation.description = Documentation.getRemarks(method)

                if (isDeprecated(method)) {
                    operation.deprecated = true
                }

                if (tag != null) {
                    operation.addTagsItem(tag.name)
                }

                for ((key, arg) in source.arguments) {
                    if (arg.source == OperationArgumentSource.Injected) {
                        continue
                    }

                    val description = getParameterSummary(method, key)

                    when (arg.source) {
                        OperationArgumentSource.Body -> {
                            if (requestMethod != RequestMethod.Get) {
                                operation.requestBody = getRequestBody(BodyDescription(schemata.getOrCreateSchema(String::class), listOf("text/plain")), description)
                            }
                        }

                        OperationArgumentSource.Content -> {
                            if (requestMethod != RequestMethod.Get) {
                                operation.requestBody = getRequestBody(BodyDescription(schemata.getOrCreateSchema(arg.type), getFormats(handler.registry)), description)
                            }
                        }

                        OperationArgumentSource.Streamed -> {
                            if (requestMethod != RequestMethod.Get) {
                                operation.requestBody = getRequestBody(BodyDescription(getBinarySchema(), listOf("*/*")), description)
                            }
                        }

                        else -> {
                            val param = if (arg.source == OperationArgumentSource.Path) PathParameter() else QueryParameter()

                            param.name = key
                            param.description = description
                            param.schema = schemata.getOrCreateSchema(arg.type)
                            param.required = mapRequired(arg.source)

                            operation.addParametersItem(param)
                        }
                    }
                }

                if (requestMethod != RequestMethod.Get) {
                    applyDeclaredRequestBody(operation, method, schemata, handler.registry)
                }

                addRequestHeaders(operation, source, inherited, schemata)

                if (source.route.isWildcard) {
                    val param = PathParameter()

                    param.name = "remainingPath"
                    param.description = "Additional path segments to be handled by this operation"
                    param.schema = StringSchema()
                    param.required = true

                    operation.addParametersItem(param)
                }

                val responses = ApiResponses()

                for ((key, value) in getResponses(source, schemata, handler.registry, inherited)) {
                    responses.addApiResponse(key, value)
                }

                operation.responses = responses

                addResponseHeaders(operation, source, inherited, schemata)

                pathItem.operation(verb, operation)
            }
        }
    }

    private fun getVerb(method: RequestMethod): PathItem.HttpMethod? {
        val name = method.toString().uppercase()
        return PathItem.HttpMethod.entries.firstOrNull { it.name == name }
    }

    private fun mapRequired(source: OperationArgumentSource): Boolean = when (source) {
        OperationArgumentSource.Path -> true
        OperationArgumentSource.Content -> true
        else -> false
    }

    private fun getParameterSummary(method: KFunction<*>, name: String): String? {
        val parameter = method.parameters.firstOrNull { it.name.equals(name, ignoreCase = true) }

        return parameter?.let { Documentation.getSummary(it) }
    }

    private fun isDeprecated(method: KFunction<*>): Boolean =
        method.annotations.any { it is Deprecated || it.annotationClass.qualifiedName == "java.lang.Deprecated" }

    private fun addRequestHeaders(operation: OpenApiOperation, source: Operation, inherited: List<OperationDocumentation>, schemata: SchemaManager) {
        for (header in getDocumentation<ApiRequestHeader>(source, inherited)) {
            if (operation.parameters?.any { it is HeaderParameter && it.name.equals(header.name, ignoreCase = true) } == true) {
                continue
            }

            val param = HeaderParameter()

            param.name = header.name
            param.description = header.description.ifEmpty { null }
            param.required = header.required
            param.schema = getSchema(header.type, schemata)

            operation.addParametersItem(param)
        }
    }

    // region Precedence

    /**
     * Collects the documentation of the given kind that applies to the operation, the most specific
     * first: the method, the service and the concerns from the innermost to the outermost.
     */
    private inline fun <reified T : Annotation> getDocumentation(operation: Operation, inherited: List<OperationDocumentation>): List<T> {
        val result = mutableListOf<T>()

        result.addAll(getFunctionAnnotations<T>(operation.method))

        for (level in getSharedLevels(operation, inherited)) {
            result.addAll(level.filterIsInstance<T>())
        }

        return result
    }

    /**
     * The documentation shared by multiple operations (declared on the service or provided by
     * concerns), grouped by the level it has been declared on, the most specific first.
     */
    private fun getSharedLevels(operation: Operation, inherited: List<OperationDocumentation>): List<List<Annotation>> {
        val result = mutableListOf<List<Annotation>>()

        getDeclaringClass(operation)?.let { result.add(getClassDocumentation(it)) }

        for (level in inherited) {
            result.add(level.entries)
        }

        return result
    }

    /**
     * Reads the annotations of the given kind from the function, including the ones declared
     * on the functions it overrides.
     */
    private inline fun <reified T : Annotation> getFunctionAnnotations(function: KFunction<*>): List<T> {
        val result = mutableListOf<T>()

        result.addAll(function.findAnnotations<T>())

        for (overridden in Documentation.overridden(function)) {
            result.addAll(overridden.findAnnotations<T>())
        }

        return result
    }

    /**
     * Reads the documentation shared by all operations from the service class (and its base classes).
     */
    private fun getClassDocumentation(type: KClass<*>): List<Annotation> {
        val result = mutableListOf<Annotation>()

        for (current in getClassHierarchy(type)) {
            result.addAll(current.findAnnotations<ApiRequestBody>())
            result.addAll(current.findAnnotations<ApiRequestHeader>())
            result.addAll(current.findAnnotations<ApiResponse>())
            result.addAll(current.findAnnotations<ApiResponseHeader>())
        }

        return result
    }

    private fun getClassHierarchy(type: KClass<*>): List<KClass<*>> {
        val result = mutableListOf<KClass<*>>()

        var current: Class<*>? = type.java

        while (current != null && current != Any::class.java) {
            runCatching { current.kotlin }.getOrNull()?.let { result.add(it) }
            current = current.superclass
        }

        return result
    }

    /**
     * Determines the class the function has been declared in. Returns null for inline lambdas and
     * for classes generated by the compiler.
     */
    private fun getDeclaringClass(operation: Operation): KClass<*>? {
        if (operation.delegate != null) {
            return null
        }

        val type = runCatching { operation.method.javaMethod?.declaringClass }.getOrNull() ?: return null

        if (type.isSynthetic || type.isAnonymousClass || type.name.contains("\$\$Lambda")) {
            return null
        }

        return runCatching { type.kotlin }.getOrNull()
    }

    // endregion

    // region Tags

    private fun getTag(operation: Operation): OpenApiTag? {
        val method = operation.method

        val methodTag = method.findAnnotation<Tag>() ?: Documentation.overridden(method).firstNotNullOfOrNull { it.findAnnotation<Tag>() }

        if (methodTag != null) {
            return OpenApiTag().name(methodTag.name)
        }

        if (operation.delegate != null) {
            return OpenApiTag().name("Inline")
        }

        val type = runCatching { method.javaMethod?.declaringClass }.getOrNull() ?: return null

        if (type.isSynthetic || type.isAnonymousClass || type.name.contains("\$\$Lambda")) {
            return OpenApiTag().name("Inline")
        }

        val kotlinType = runCatching { type.kotlin }.getOrNull()

        return OpenApiTag()
            .name(kotlinType?.let { findClassAnnotation<Tag>(it) }?.name ?: type.simpleName)
            .description(kotlinType?.let { Documentation.getDescription(it) })
    }

    private inline fun <reified T : Annotation> findClassAnnotation(type: KClass<*>): T? =
        getClassHierarchy(type).firstNotNullOfOrNull { it.findAnnotation<T>() }

    private fun addTag(document: OpenAPI, tag: OpenApiTag) {
        val existing = document.tags?.firstOrNull { it.name == tag.name }

        if (existing == null) {
            document.addTagsItem(tag)
        } else if (existing.description.isNullOrEmpty()) {
            existing.description = tag.description
        }
    }

    // endregion

    // region Request

    private fun getRequestBody(body: BodyDescription, description: String?): OpenApiRequestBody {
        val requestBody = OpenApiRequestBody()

        requestBody.description = description
        requestBody.content = Content()

        for (mediaType in body.mediaTypes) {
            requestBody.content.addMediaType(mediaType, MediaType().schema(body.schema))
        }

        return requestBody
    }

    private fun applyDeclaredRequestBody(operation: OpenApiOperation, method: KFunction<*>, schemata: SchemaManager, registry: MethodRegistry) {
        val declarations = getFunctionAnnotations<ApiRequestBody>(method)

        if (declarations.isEmpty()) {
            return
        }

        val inferred = operation.requestBody

        val requestBody = OpenApiRequestBody()

        requestBody.description = declarations.map { it.description.ifEmpty { null } }.firstOrNull { it != null } ?: inferred?.description
        requestBody.content = Content()

        for (declaration in declarations) {
            val body = getDeclaredBody(declaration.type, declaration.contentType, getInferredBody(inferred), schemata, registry) ?: continue

            for (mediaType in body.mediaTypes) {
                if (!requestBody.content.containsKey(mediaType)) {
                    requestBody.content.addMediaType(mediaType, MediaType().schema(body.schema))
                }
            }
        }

        // only the description has been declared, so keep the content as derived from the signature
        if (requestBody.content.isEmpty() && inferred?.content != null) {
            for ((mediaType, content) in inferred.content) {
                requestBody.content.addMediaType(mediaType, content)
            }
        }

        if (requestBody.content.isNotEmpty()) {
            requestBody.required = true
            operation.requestBody = requestBody
        }
    }

    private fun getInferredBody(body: OpenApiRequestBody?): BodyDescription? {
        if (body?.content == null || body.content.isEmpty()) {
            return null
        }

        return BodyDescription(body.content.values.first().schema, body.content.keys.toList())
    }

    // endregion

    // region Responses

    private fun getResponses(operation: Operation, schemata: SchemaManager, registry: MethodRegistry, inherited: List<OperationDocumentation>): Map<String, OpenApiResponse> {
        val result = LinkedHashMap<String, OpenApiResponse>()

        val method = operation.method

        val resultSummary = Documentation.getResultSummary(method)

        val inferred = getResultBody(operation, schemata, registry)

        val declared = getFunctionAnnotations<ApiResponse>(method)

        if (declared.none { isSuccess(it.status) }) {
            addInferredResponses(result, operation, inferred, resultSummary)
        }

        for ((status, group) in declared.groupBy { it.status }) {
            result[getKey(status)] = getDeclaredResponse(status, group, inferred, resultSummary, schemata, registry)
        }

        // responses shared by the service or declared by concerns, the most specific level wins
        for (level in getSharedLevels(operation, inherited)) {
            for ((status, group) in level.filterIsInstance<ApiResponse>().groupBy { it.status }) {
                result.putIfAbsent(getKey(status), getDeclaredResponse(status, group, null, null, schemata, registry))
            }
        }

        return result
    }

    private fun addResponseHeaders(operation: OpenApiOperation, source: Operation, inherited: List<OperationDocumentation>, schemata: SchemaManager) {
        for (header in getDocumentation<ApiResponseHeader>(source, inherited)) {
            val status = header.status.firstOrNull()

            val targets: List<OpenApiResponse> = if (status != null) {
                listOfNotNull(operation.responses?.get(getKey(status)))
            } else {
                operation.responses.entries
                    .filter { it.key.toIntOrNull()?.let { code -> code in 200..299 } == true }
                    .map { it.value }
            }

            for (target in targets) {
                if (target.headers?.keys?.any { it.equals(header.name, ignoreCase = true) } == true) {
                    continue
                }

                target.addHeaderObject(
                    header.name,
                    Header().description(header.description.ifEmpty { null }).schema(getSchema(header.type, schemata))
                )
            }
        }
    }

    private fun addInferredResponses(result: MutableMap<String, OpenApiResponse>, operation: Operation, body: BodyDescription?, resultSummary: String?) {
        val sink = operation.result.sink

        if (sink == OperationResultSink.None || operation.result.type.mightBeNull()) {
            result["204"] = OpenApiResponse().description("A response containing no body")
        }

        if (body != null) {
            val response = OpenApiResponse().description("")

            if (sink == OperationResultSink.Binary || sink == OperationResultSink.Dynamic) {
                response.description = resultSummary ?: "A dynamically generated response"
            } else if (resultSummary != null) {
                response.description = resultSummary
            }

            addContent(response, body)

            result["200"] = response
        }
    }

    private fun getDeclaredResponse(
        status: ResponseStatus,
        declarations: List<ApiResponse>,
        inferred: BodyDescription?,
        resultSummary: String?,
        schemata: SchemaManager,
        registry: MethodRegistry
    ): OpenApiResponse {
        val response = OpenApiResponse()

        var description: String? = null

        for (declaration in declarations) {
            if (description == null) {
                description = declaration.description.ifEmpty { null }
            }

            val body = getDeclaredBody(declaration.type, declaration.contentType, if (isSuccess(status)) inferred else null, schemata, registry)

            if (body != null) {
                addContent(response, body)
            }
        }

        response.description = description ?: resultSummary ?: getDefaultDescription(status)

        return response
    }

    private fun addContent(response: OpenApiResponse, body: BodyDescription) {
        val content = response.content ?: Content().also { response.content = it }

        for (mediaType in body.mediaTypes) {
            if (!content.containsKey(mediaType)) {
                content.addMediaType(mediaType, MediaType().schema(body.schema))
            }
        }
    }

    /**
     * Describes the content generated by the method, as derived from its signature.
     */
    private fun getResultBody(operation: Operation, schemata: SchemaManager, registry: MethodRegistry): BodyDescription? {
        val type = unwrap(operation.result.type)

        return when (operation.result.sink) {
            OperationResultSink.Formatter -> BodyDescription(schemata.getOrCreateSchema(type), listOf("text/plain"))
            OperationResultSink.Serializer -> BodyDescription(schemata.getOrCreateSchema(type), getFormats(registry))
            OperationResultSink.Binary -> BodyDescription(getBinarySchema(), listOf("application/octet-stream"))
            OperationResultSink.Dynamic -> BodyDescription(null, listOf("*/*"))
            else -> null
        }
    }

    /**
     * Results wrapped into a [org.genhttp.modules.reflection.Result] are not unwrapped by the signature
     * analysis if the type is not known, but are still serialized (see ResponseProvider).
     */
    private fun unwrap(type: KType): KType {
        val classifier = type.classifier as? KClass<*>

        if (classifier != null && classifier.isSubclassOf(ResultWrapper::class)) {
            type.arguments.firstOrNull()?.type?.let { return it }
        }

        return type
    }

    private fun isSuccess(status: ResponseStatus): Boolean = status.code in 200..299

    private fun getKey(status: ResponseStatus): String = status.code.toString()

    private fun getDefaultDescription(status: ResponseStatus): String = when (status) {
        ResponseStatus.NoContent -> "A response containing no body"
        else -> WordBoundaryMatcher.replace(status.name, " ")
    }

    // endregion

    // region Bodies

    /**
     * Describes the content of a body declared by an annotation, using the body derived from
     * the method signature to fill the information that has not been declared.
     */
    private fun getDeclaredBody(type: KClass<*>, contentType: String, inferred: BodyDescription?, schemata: SchemaManager, registry: MethodRegistry): BodyDescription? {
        val declaredContentType = contentType.ifEmpty { null }

        if (type != Nothing::class) {
            val schema = schemata.getOrCreateSchema(type)

            if (declaredContentType != null) {
                return BodyDescription(schema, listOf(declaredContentType))
            }

            return BodyDescription(schema, if (registry.formatting.canHandle(type)) listOf("text/plain") else getFormats(registry))
        }

        if (inferred != null) {
            if (declaredContentType == null) {
                return inferred
            }

            return BodyDescription(inferred.schema ?: guessSchema(declaredContentType), listOf(declaredContentType))
        }

        return if (declaredContentType != null) BodyDescription(guessSchema(declaredContentType), listOf(declaredContentType)) else null
    }

    private fun guessSchema(contentType: String): Schema<*>? {
        val mediaType = contentType.substringBefore(';').trim()

        if (mediaType.startsWith("text/", ignoreCase = true)) {
            return StringSchema()
        }

        if (mediaType.endsWith("json", ignoreCase = true) || mediaType.endsWith("xml", ignoreCase = true) || mediaType.endsWith("yaml", ignoreCase = true)) {
            return null
        }

        return getBinarySchema()
    }

    private fun getBinarySchema(): Schema<*> = Schema<Any>().format("binary")

    private fun getSchema(type: KClass<*>, schemata: SchemaManager): Schema<*> =
        if (type == Nothing::class) StringSchema() else schemata.getOrCreateSchema(type)

    private fun getFormats(registry: MethodRegistry): List<String> = registry.serialization.formats.keys.map { it.toString() }

    // endregion

    private companion object {

        val WordBoundaryMatcher = Regex("(?<=[a-z])(?=[A-Z])")

    }

}
