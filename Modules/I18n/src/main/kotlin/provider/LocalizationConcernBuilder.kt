package org.genhttp.modules.i18n.provider

import kotlinx.coroutines.flow.flow

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getCookie
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.i18n.CurrentCulture
import org.genhttp.modules.i18n.parsers.CultureInfoParser

import java.util.Locale

/**
 * Builder class to configure and create an instance of [LocalizationConcern].
 */
public class LocalizationConcernBuilder : ConcernBuilder {

    // region Fields

    private var defaultCulture: Locale = Locale.getDefault(Locale.Category.DISPLAY)

    private val cultureSelectors = mutableListOf<CultureSelectorAsyncDelegate>()

    private var cultureFilter: CultureFilterAsyncDelegate = { _, _ -> true }

    private val cultureSetters = mutableListOf<AsyncOrSyncSetter>()

    // endregion

    // region Functionality

    // region Selectors

    /**
     * Extracts the language from a cookie.
     *
     * @param cookieName The name of the cookie to extract the language from
     */
    public fun fromCookie(cookieName: String = "lang"): LocalizationConcernBuilder =
        fromRequest { request -> request.header.headers.getCookie(cookieName) }

    /**
     * Extracts the language from a query parameter.
     *
     * @param queryName The name of the query parameter to extract the language from
     */
    public fun fromQuery(queryName: String = "lang"): LocalizationConcernBuilder =
        fromRequest { request -> request.header.query.getEntry(queryName) }

    /**
     * Extracts the language from a header.
     *
     * @param headerName The name of the header to extract the language from
     */
    public fun fromHeader(headerName: String = "Accept-Language"): LocalizationConcernBuilder =
        fromRequest { request -> request.header.headers.getEntry(headerName) }

    /**
     * Extracts the language from a custom (async) selector.
     *
     * @param languageSelector The selector to extract the language from, e.g. the value of a language header
     */
    public fun fromRequest(languageSelector: suspend (Request) -> String?): LocalizationConcernBuilder =
        fromCultures { request ->
            val language = languageSelector(request)
            CultureInfoParser.parseFromLanguage(language)
        }

    /**
     * Extracts the culture from a custom (async) selector. Named differently than [fromRequest] as Kotlin cannot
     * overload lambdas by their return type (`FromRequest` in GenHTTP).
     *
     * @param cultureSelector The selector to extract the culture from
     */
    public fun fromCultures(cultureSelector: CultureSelectorAsyncDelegate): LocalizationConcernBuilder {
        cultureSelectors.add(cultureSelector)
        return this
    }

    // endregion

    // region Filters

    /**
     * Sets the supported cultures.
     *
     * @param supportedCultures The supported cultures
     */
    public fun supports(vararg supportedCultures: Locale): LocalizationConcernBuilder {
        val closure = supportedCultures.toHashSet()
        return supports { culture -> closure.contains(culture) }
    }

    /**
     * Sets a custom (async) filter of supported cultures.
     *
     * @param culturePredicate The predicate to filter the cultures
     */
    public fun supports(culturePredicate: suspend (Locale) -> Boolean): LocalizationConcernBuilder =
        supports { _, culture -> culturePredicate(culture) }

    /**
     * Sets a custom (async) filter (using the [Request]) of supported cultures.
     *
     * @param cultureFilter The filter to filter the cultures
     */
    public fun supports(cultureFilter: CultureFilterAsyncDelegate): LocalizationConcernBuilder {
        this.cultureFilter = cultureFilter
        return this
    }

    // endregion

    // region Setters

    /**
     * Sets the current culture and UI culture. As the JVM has no thread-bound current culture that survives
     * suspension, the selected culture is stored in the properties of the request
     * (see [CurrentCulture.CultureProperty] and [CurrentCulture.UiCultureProperty]) and made available to the
     * wrapped content via [CurrentCulture.culture] and [CurrentCulture.uiCulture].
     *
     * @param currentCulture Whether to set the current culture
     * @param currentUiCulture Whether to set the current UI culture
     * @throws IllegalArgumentException Thrown when both flags are set to false
     */
    public fun setter(currentCulture: Boolean = false, currentUiCulture: Boolean = true): LocalizationConcernBuilder {
        require(currentCulture || currentUiCulture) { "At least one of the flags must be set to true." }

        // Note: this is a minor optimization so that the flags are not evaluated for each request
        if (currentCulture) {
            setter { request, culture -> request.properties[CurrentCulture.CultureProperty] = culture }
        }

        if (currentUiCulture) {
            setter { request, culture -> request.properties[CurrentCulture.UiCultureProperty] = culture }
        }

        return this
    }

    /**
     * Sets a custom (async) culture setter.
     *
     * @param cultureSetter The culture setter
     */
    public fun setter(cultureSetter: suspend (Locale) -> Unit): LocalizationConcernBuilder =
        setter { _, culture -> cultureSetter(culture) }

    /**
     * Sets a custom (async) culture setter (using the [Request]).
     *
     * @param cultureSetter The culture setter
     */
    public fun setter(cultureSetter: CultureSetterAsyncDelegate): LocalizationConcernBuilder {
        cultureSetters.add(AsyncOrSyncSetter(asyncSetter = cultureSetter))
        return this
    }

    // endregion

    // region Default

    /**
     * Sets the default culture that is used as a fallback.
     *
     * @param culture The default culture
     */
    public fun default(culture: Locale): LocalizationConcernBuilder {
        defaultCulture = culture
        return this
    }

    // endregion

    override fun build(content: Handler): Concern {
        if (cultureSelectors.isEmpty()) {
            fromHeader()
        }

        if (cultureSetters.isEmpty()) {
            setter()
        }

        return LocalizationConcern(
            content,
            defaultCulture,
            ::cultureSelector,
            cultureFilter,
            cultureSetters.toList()
        )
    }

    // endregion

    // region Composite functions

    private fun cultureSelector(request: Request) = flow {
        for (selector in cultureSelectors) {
            val cultures = selector(request)

            for (culture in cultures) {
                emit(culture)
            }
        }
    }

    // endregion

}
