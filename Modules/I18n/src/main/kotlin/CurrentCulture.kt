package org.genhttp.modules.i18n

import java.util.Locale

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The cultures that have been selected for the request that is currently handled. There is no thread-bound
 * current culture on the JVM that survives suspension, so the localization concern keeps the cultures
 * in the coroutine context of the handlers it wraps (and in the properties of the request).
 *
 * @property culture The culture used to format values (see [CurrentCulture.culture]), if set
 * @property uiCulture The culture used to look up resources (see [CurrentCulture.uiCulture]), if set
 */
public class CultureContext(public val culture: Locale?, public val uiCulture: Locale?) : AbstractCoroutineContextElement(Key) {

    /**
     * The key of this element within a coroutine context.
     */
    public companion object Key : CoroutineContext.Key<CultureContext>

}

/**
 * Provides access to the culture that has been selected for the request currently being handled by a
 * [org.genhttp.modules.i18n.provider.LocalizationConcern] (the counterpart of `CultureInfo.CurrentCulture` and
 * `CultureInfo.CurrentUICulture`).
 */
public object CurrentCulture {

    /**
     * The key of the request property the culture is stored in by `setter(currentCulture = true)`.
     */
    public const val CultureProperty: String = "__CURRENT_CULTURE"

    /**
     * The key of the request property the UI culture is stored in by `setter(currentUiCulture = true)`.
     */
    public const val UiCultureProperty: String = "__CURRENT_UI_CULTURE"

    /**
     * The culture that has been set for the current request (or the default format locale of the JVM).
     */
    public suspend fun culture(): Locale = coroutineContext[CultureContext]?.culture ?: Locale.getDefault(Locale.Category.FORMAT)

    /**
     * The UI culture that has been set for the current request (or the default display locale of the JVM).
     */
    public suspend fun uiCulture(): Locale = coroutineContext[CultureContext]?.uiCulture ?: Locale.getDefault(Locale.Category.DISPLAY)

}
