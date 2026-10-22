package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.getContent

import java.net.http.HttpResponse

/**
 * The result of parsing an OpenAPI document (`ReadResult` of Microsoft.OpenApi), consisting of the document and
 * the problems found while reading it.
 */
internal class ReadResult(val document: OpenAPI?, val errors: List<String>, val warnings: List<String>)

internal object Extensions {

    suspend fun getOperation(engine: ServerEngine, api: InlineBuilder): Pair<Pair<String, PathItem>, Operation> {
        val doc = api.add(ApiDescription.create()).getOpenApi(engine).document!!

        val path = doc.paths.entries.first()

        return Pair(Pair(path.key, path.value), path.value.readOperationsMap().values.first())
    }

}

private val ReferencePattern = Regex("""\${'$'}ref"?\s*:\s*["']#/components/schemas/([^"']+)["']""")

internal fun HttpResponse<ByteArray>.asOpenApi(): ReadResult {
    assertStatus(ResponseStatus.Ok)

    val content = getContent()

    // JSON is a subset of YAML, but the JSON reader is the more strict one
    val document = if (content.trimStart().startsWith("{")) {
        Json.mapper().readValue(content, OpenAPI::class.java)
    } else {
        Yaml.mapper().readValue(content, OpenAPI::class.java)
    }

    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    if (document == null) {
        errors.add("The document is empty")
    } else {
        if (document.openapi.isNullOrEmpty()) {
            errors.add("The document does not specify the OpenAPI version")
        }

        if (document.info == null) {
            errors.add("The document does not provide the required info object")
        }

        val schemas = document.components?.schemas ?: emptyMap()

        for (match in ReferencePattern.findAll(content)) {
            if (!schemas.containsKey(match.groupValues[1])) {
                errors.add("The reference '${match.value}' cannot be resolved")
            }
        }
    }

    return ReadResult(document, errors, warnings)
}

internal suspend fun HandlerBuilder.getOpenApi(engine: ServerEngine, validate: Boolean = true): ReadResult {
    TestHost.run(this, engine = engine).use { host ->
        val response = host.getResponse("/openapi.json")

        val result = response.asOpenApi()

        if (validate) {
            AssertX.empty(result.errors)
            AssertX.empty(result.warnings)
        }

        return result
    }
}
