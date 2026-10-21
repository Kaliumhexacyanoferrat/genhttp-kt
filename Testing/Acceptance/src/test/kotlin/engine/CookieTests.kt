package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.CookieOptions
import org.genhttp.api.protocol.SameSite
import org.genhttp.api.protocol.cookie
import org.genhttp.api.protocol.getCookie
import org.genhttp.api.protocol.getCookies
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.acceptance.wrap

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class CookieTests {

    @MultiEngineTest
    fun testCookieCanBeRead(engine: ServerEngine) = test {
        var cookie: String? = null

        val handler = FunctionalHandler { r ->
            cookie = r.header.headers.getCookie("session")

            r.respond().build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Cookie", "first=1; session=abc123; third=3")

            runner.getResponse(request)

            assertEquals("abc123", cookie)
        }
    }

    @MultiEngineTest
    fun testMissingCookieReturnsNull(engine: ServerEngine) = test {
        var cookie: String? = "not-null"

        val handler = FunctionalHandler { r ->
            cookie = r.header.headers.getCookie("missing")

            r.respond().build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Cookie", "first=1; second=2")

            runner.getResponse(request)

            assertNull(cookie)
        }
    }

    @MultiEngineTest
    fun testCookiesCanBeIterated(engine: ServerEngine) = test {
        var cookies: MutableList<Pair<String, String>>? = null

        val handler = FunctionalHandler { r ->
            val list = r.header.headers.getCookies()

            val found = mutableListOf<Pair<String, String>>()

            for (i in 0 until list.count) {
                val entry = list.getStringEntry(i)

                found.add(entry.first.toString() to entry.second.toString())
            }

            cookies = found

            r.respond().build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Cookie", "first=1; second=2")

            runner.getResponse(request)

            assertEquals<List<Pair<String, String>>?>(listOf("first" to "1", "second" to "2"), cookies)
        }
    }

    @MultiEngineTest
    fun testCookiesCanBeLookedUpByName(engine: ServerEngine) = test {
        var cookie: String? = null

        val handler = FunctionalHandler { r ->
            cookie = r.header.headers.getCookies().getEntry("second")

            r.respond().build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Cookie", "first=1; second=2")

            runner.getResponse(request)

            assertEquals("2", cookie)
        }
    }

    @MultiEngineTest
    fun testCookieCanBeWritten(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r -> r.respond().cookie("session", "abc123").build() }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            val values = response.headers().allValues("Set-Cookie")

            assertEquals(listOf("session=abc123"), values)
        }
    }

    @MultiEngineTest
    fun testCookieWithOptionsCanBeWritten(engine: ServerEngine) = test {
        val expires = Instant.parse("2030-01-02T03:04:05Z")

        val options = CookieOptions(
            expires = expires,
            maxAge = 86400.seconds,
            domain = ByteString("example.com"),
            path = ByteString("/api"),
            secure = true,
            httpOnly = true,
            sameSite = SameSite.Strict
        )

        val handler = FunctionalHandler { r -> r.respond().cookie("session", "abc123", options).build() }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            val values = response.headers().allValues("Set-Cookie")

            val expected = "session=abc123; Expires=${rfc1123(expires)}; Max-Age=86400; Domain=example.com; Path=/api; Secure; HttpOnly; SameSite=Strict"

            assertEquals(listOf(expected), values)
        }
    }

    @MultiEngineTest
    fun testCookieWithExpiresOnlyCanBeWritten(engine: ServerEngine) = test {
        val expires = Instant.parse("2030-01-02T03:04:05Z")

        val options = CookieOptions(expires = expires)

        val handler = FunctionalHandler { r -> r.respond().cookie("session", "abc123", options).build() }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            val values = response.headers().allValues("Set-Cookie")

            val expected = "session=abc123; Expires=${rfc1123(expires)}"

            assertEquals(listOf(expected), values)
        }
    }

    @MultiEngineTest
    fun testMultipleCookiesCanBeWritten(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r -> r.respond().cookie("first", "1").cookie("second", "2").build() }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            val values = response.headers().allValues("Set-Cookie")

            assertEquals(listOf("first=1", "second=2"), values)
        }
    }

    /**
     * The "r" format specifier of .NET (RFC 1123).
     */
    private fun rfc1123(value: Instant): String =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC).format(value)

}
