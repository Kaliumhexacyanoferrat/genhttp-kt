package org.genhttp.testing.acceptance.modules.redirects

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.redirects.Redirect
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getHeader

import java.net.URI

import kotlin.test.assertEquals

class RedirectTests {

    @MultiEngineTest
    fun testTemporary(engine: ServerEngine) = test {
        val redirect = Redirect.to("https://google.de/", true)

        TestHost.run(redirect, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.TemporaryRedirect)
            assertEquals("https://google.de/", response.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testTemporaryPost(engine: ServerEngine) = test {
        val redirect = Redirect.to("https://google.de/", true)

        TestHost.run(redirect, engine = engine).use { runner ->
            val request = runner.getRequest(method = "POST")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.SeeOther)
            assertEquals("https://google.de/", response.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testPermanent(engine: ServerEngine) = test {
        val redirect = Redirect.to("https://google.de/")

        TestHost.run(redirect, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.MovedPermanently)
            assertEquals("https://google.de/", response.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testPermanentPost(engine: ServerEngine) = test {
        val redirect = Redirect.to("https://google.de/")

        TestHost.run(redirect, engine = engine).use { runner ->
            val request = runner.getRequest(method = "POST")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.PermanentRedirect)
            assertEquals("https://google.de/", response.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testAbsoluteRoute(engine: ServerEngine) = test {
        val layout = Layout.create()
            .add("redirect", Redirect.to("/me/to/"))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse("/redirect/")

            assertEquals("/me/to/", URI(response.getHeader("Location")!!).path)
        }
    }

}
