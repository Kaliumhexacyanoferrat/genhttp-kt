package org.genhttp.testing.acceptance.modules.i18n

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.i18n.Localization
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader

import java.util.Locale

import kotlin.test.assertEquals
import kotlin.test.assertNull

open class ContentLanguageTests {

    @MultiEngineTest
    fun testContentLanguage(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Content.from(Resource.fromString("Hello World")))
            .add(Localization.create().fromQuery())

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/?lang=de")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("de-DE", response.getContentHeader("Content-Language"))
        }
    }

    @MultiEngineTest
    fun testInvariantNoLanguage(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Content.from(Resource.fromString("Hello World")))
            .add(Localization.create().default(Locale.ROOT))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertNull(response.getContentHeader("Content-Language"))
        }
    }

    @MultiEngineTest
    fun testNotFoundNoLanguage(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(Localization.create().fromQuery())

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/?lang=de")

            response.assertStatus(ResponseStatus.NotFound)

            assertNull(response.getContentHeader("Content-Language"))
        }
    }

}
