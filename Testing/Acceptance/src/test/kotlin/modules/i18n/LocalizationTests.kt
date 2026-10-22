package org.genhttp.testing.acceptance.modules.i18n

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.i18n.CurrentCulture
import org.genhttp.modules.i18n.Localization
import org.genhttp.modules.i18n.provider.LocalizationConcernBuilder
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.net.http.HttpRequest
import java.util.Locale

import kotlin.test.assertEquals

class LocalizationTests {

    @MultiEngineTest
    fun testDefaultBehavior(engine: ServerEngine) = test {
        val currentCulture = Locale.getDefault(Locale.Category.DISPLAY)

        val localization = Localization.create()

        testLocalization(engine, localization) { _ ->
            assertEquals(currentCulture, CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testDefault(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .default(Locale.forLanguageTag("fr-FR"))

        testLocalization(engine, localization) { _ ->
            assertEquals(Locale.forLanguageTag("fr-FR"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testFromStatic(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromRequest { _ -> listOf(Locale.forLanguageTag("fr-FR")) }

        testLocalization(engine, localization) { _ ->
            assertEquals(Locale.forLanguageTag("fr-FR"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testFromQuery(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromQuery()

        testLocalization(engine, localization, path = "?lang=cs-CZ") { _ ->
            assertEquals(Locale.forLanguageTag("cs-CZ"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testFromHeader(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromHeader()

        testLocalization(engine, localization, requestSetup = { request ->
            request.header("Accept-Language", "mn-MN")
        }) { _ ->
            assertEquals(Locale.forLanguageTag("mn-MN"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testFromCookie(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromCookie()

        testLocalization(engine, localization, requestSetup = { request ->
            request.header("Cookie", "lang=quz-EC")
        }) { _ ->
            assertEquals(Locale.forLanguageTag("quz-EC"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testSupportsList(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromRequest { _ -> "en,de,cs,fr" }
            .supports(Locale.forLanguageTag("fr-FR"), Locale.forLanguageTag("cs-CZ"))

        testLocalization(engine, localization) { _ ->
            assertEquals(Locale.forLanguageTag("cs-CZ"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testSupportsPredicate(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromRequest { _ -> "en,de,cs,fr" }
            .supports { culture -> culture == Locale.forLanguageTag("fr-FR") }

        testLocalization(engine, localization) { _ ->
            assertEquals(Locale.forLanguageTag("fr-FR"), CurrentCulture.uiCulture())
        }
    }

    @MultiEngineTest
    fun testSetterCurrentCulture(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromRequest { _ -> listOf(Locale.forLanguageTag("fr-FR")) }
            .setter(currentCulture = true, currentUiCulture = false)

        testLocalization(engine, localization) { _ ->
            assertEquals(Locale.forLanguageTag("fr-FR"), CurrentCulture.culture())
        }
    }

    @MultiEngineTest
    fun testSetterCustom(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromRequest { _ -> "de" }
            .setter { request, culture -> request.properties["culture"] = culture }

        testLocalization(engine, localization) { request ->
            assertEquals(Locale.forLanguageTag("de-DE"), request.properties["culture"])
        }
    }

    @MultiEngineTest
    fun testMultipleMixed(engine: ServerEngine) = test {
        val localization = Localization
            .create()
            .fromQuery()
            .fromCookie()
            .fromHeader()
            .fromRequest { _ -> "de" }
            .supports(Locale.forLanguageTag("de-DE"))
            .setter(currentCulture = true)
            .setter { request, culture -> request.properties["culture"] = culture }

        testLocalization(engine, localization, path = "?lang=cs-CZ", requestSetup = { request ->
            request.header("Accept-Language", "mn-MN")
            request.header("Cookie", "lang=quz-EC")
        }) { request ->
            val expected = Locale.forLanguageTag("de-DE")

            assertEquals(expected, request.properties["culture"])
            assertEquals(expected, CurrentCulture.culture())
            assertEquals(expected, CurrentCulture.uiCulture())
        }
    }

    private suspend fun testLocalization(
        engine: ServerEngine,
        localization: LocalizationConcernBuilder,
        path: String? = null,
        requestSetup: ((HttpRequest.Builder) -> Unit)? = null,
        requestAssert: suspend (Request) -> Unit
    ) {
        var assertException: Throwable? = null

        // note: there is no thread-bound current culture on the JVM to be reset to the invariant culture here

        val handler = Inline
            .create()
            .add(localization)
            .get { request: Request ->
                try {
                    requestAssert(request)
                } catch (e: Throwable) {
                    assertException = e
                }
            }

        TestHost.run(handler, engine = engine).use { host ->
            val request = host.getRequest(path)
            requestSetup?.invoke(request)

            host.getResponse(request)
        }

        val exception = assertException

        if (exception != null) {
            throw exception
        }
    }

}
