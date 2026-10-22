package org.genhttp.modules.i18n.provider

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

import org.genhttp.api.content.Handler
import org.genhttp.api.content.services.ApiResponseHeader
import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.tryGet
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.i18n.CultureContext
import org.genhttp.modules.i18n.CurrentCulture

import java.util.Locale

/**
 * Selects the culture to be used for a request and makes it available to the wrapped content.
 *
 * @param content The handler to be wrapped by this concern
 * @param defaultCulture The culture to be used if no (supported) culture could be determined
 * @param cultureSelector Provides the candidates for the culture of a request
 * @param cultureFilter Decides whether a candidate is supported
 * @param cultureSetters The setters that are invoked with the selected culture
 */
public class LocalizationConcern(
    override val content: Handler,
    private val defaultCulture: Locale,
    private val cultureSelector: CultureSelectorCombinedAsyncDelegate,
    private val cultureFilter: CultureFilterAsyncDelegate,
    private val cultureSetters: List<AsyncOrSyncSetter>
) : DocumentedConcern {

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        operation.add(ApiResponseHeader("Content-Language", "The language of the returned content"))
    }

    override suspend fun handle(request: Request): Response? {
        val culture = resolveCultureInfo(request) ?: defaultCulture

        for (cultureSetter in cultureSetters) {
            cultureSetter.syncSetter?.invoke(request, culture)

            cultureSetter.asyncSetter?.invoke(request, culture)
        }

        val response = handleContent(request)

        if (response?.content != null) {
            val tag = if (culture.language.isEmpty()) "" else culture.toLanguageTag()

            if (tag.isNotEmpty()) {
                return response.rebuild()
                    .header("Content-Language", tag)
                    .build()
            }
        }

        return response
    }

    private suspend fun handleContent(request: Request): Response? {
        // there is no thread-bound current culture on the JVM, so we keep it in the coroutine context
        val current = request.properties.tryGet<Locale>(CurrentCulture.CultureProperty)
        val currentUi = request.properties.tryGet<Locale>(CurrentCulture.UiCultureProperty)

        if (current == null && currentUi == null) {
            return content.handle(request)
        }

        return withContext(CultureContext(current, currentUi)) {
            content.handle(request)
        }
    }

    private suspend fun resolveCultureInfo(request: Request): Locale? =
        cultureSelector(request).firstOrNull { cultureFilter(request, it) }

    // endregion

}
