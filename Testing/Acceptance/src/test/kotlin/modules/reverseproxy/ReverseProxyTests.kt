package org.genhttp.testing.acceptance.modules.reverseproxy

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.cookie
import org.genhttp.api.protocol.getCookie
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.getForwardings
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.modules.io.content
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reverseproxy.Proxy
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import org.junit.jupiter.api.Test

import java.net.CookieManager
import java.net.HttpCookie
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ReverseProxyTests {

    @MultiEngineTest
    fun testBasics(engine: ServerEngine) = test {
        TestSetup.create(engine) { r -> r.respond().content("Hello World!").build() }.use { setup ->
            val runner = setup.runner

            val response = runner.getResponse()
            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testRedirection(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond().header("Location", "http://localhost:${r.endPoint.port}/target").status(ResponseStatus.TemporaryRedirect).build()
        }.use { setup ->
            val runner = setup.runner

            val redirected = runner.getResponse("/")

            assertEquals("http://localhost:${runner.port}/target", redirected.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testHead(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val responseContent = "A".repeat(2048)
            r.respond().content(responseContent).build()
        }.use { setup ->
            val runner = setup.runner

            val headRequest = runner.getRequest(method = "HEAD")

            val headed = runner.getResponse(headRequest)

            headed.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testContentTypeIsRelayed(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val contentType = r.header.headers.getEntry("Content-Type")
            r.respond().content(contentType ?: "(none)").build()
        }.use { setup ->
            val runner = setup.runner

            val request = runner.getRequest(method = "SEARCH")
                .method("SEARCH", BodyPublishers.ofString("<x/>"))
                .header("Content-Type", "application/xml; charset=utf-8")

            val response = runner.getResponse(request)

            assertEquals("application/xml; charset=utf-8", response.getContent())
        }
    }

    @MultiEngineTest
    fun testForwardingChainIsRelayed(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val forwardings = r.header.headers.getForwardings()

            // the proxy relays the original hop and appends one of its own
            assertEquals(2, forwardings.size)
            assertEquals(InetAddress.getByName("85.192.1.5"), forwardings[0].forAddress)
            assertEquals("google.com", forwardings[0].host)

            assertTrue(forwardings[1].forAddress!!.isLoopbackAddress)

            r.respond().content("Hello World!").build()
        }.use { setup ->
            val runner = setup.runner

            val request = runner.getRequest()
            request.header("Forwarded", "for=85.192.1.5; host=google.com")

            val response = runner.getResponse(request)
            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testCookies(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            assertEquals("World", r.header.headers.getCookie("Hello"))

            r.respond()
                .content("Hello World!")
                .cookie("One", "1")
                .cookie("Two", "2")
                .build()
        }.use { setup ->
            val runner = setup.runner

            val cookies = CookieManager()

            cookies.cookieStore.add(
                URI.create("http://localhost"),
                HttpCookie("Hello", "World").apply {
                    path = "/"
                    domain = "localhost"
                    version = 0 // netscape style, the JDK would quote the value otherwise
                }
            )

            val client = TestHost.getClient(cookies = cookies)

            val cookieRequest = runner.getRequest()

            val cookied = runner.getResponse(cookieRequest, client)

            cookied.assertStatus(ResponseStatus.Ok)

            val returned = cookies.cookieStore.get(URI.create(runner.getUrl())).associateBy { it.name }

            assertEquals("1", returned["One"]!!.value)
            assertEquals("2", returned["Two"]!!.value)
        }
    }

    @MultiEngineTest
    fun testUpstreamCookiesDoNotBleedAcrossVisitors(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val path = r.header.target.asString(false)

            if (path == "/set") {
                // upstream issues a session cookie for visitor A
                return@create r.respond()
                    .content("set")
                    .cookie("bleed", "SECRET")
                    .build()
            }

            // echo back the Cookie header the upstream actually received
            val cookie = r.header.headers.getEntry("Cookie") ?: "none"
            r.respond().content(cookie).build()
        }.use { setup ->
            val runner = setup.runner

            // visitor A logs in; the upstream sends Set-Cookie: bleed=SECRET
            val visitorA = TestHost.getClient()

            val set = runner.getResponse("/set", visitorA)
            set.assertStatus(ResponseStatus.Ok)

            // visitor B is a brand-new client that never sent any cookie
            val visitorB = TestHost.getClient()

            val echoed = runner.getResponse("/echo", visitorB)

            // the upstream must NOT see visitor A's session cookie for visitor B
            assertEquals("none", echoed.getContent())
        }
    }

    @MultiEngineTest
    fun testHeaders(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond()
                .content("Hello World")
                .header("X-Custom-Header", r.header.headers.getEntry("X-Custom-Header") ?: "none")
                .build()
        }.use { setup ->
            val runner = setup.runner

            val request = runner.getRequest()

            request.header("X-Custom-Header", "Custom Value")

            val response = runner.getResponse(request)

            assertEquals("Custom Value", response.getHeader("X-Custom-Header"))
        }
    }

    @MultiEngineTest
    fun testPost(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val content = String(r.getBody(HeaderAccess.Retain)!!.asStream().readAllBytes(), Charsets.UTF_8)
            r.respond().content(content).build()
        }.use { setup ->
            val runner = setup.runner

            val request = runner.getRequest(method = "POST")
                .method("POST", BodyPublishers.ofString("Input"))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Input", response.getContent())
        }
    }

    @MultiEngineTest
    fun testPathing(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond().content(r.header.target.asString(false)).build()
        }.use { setup ->
            val runner = setup.runner

            val r1 = runner.getResponse("/")
            assertEquals("/", r1.getContent())

            val r2 = runner.getResponse("/login/")
            assertEquals("/login/", r2.getContent())

            val r3 = runner.getResponse("/login")
            assertEquals("/login", r3.getContent())
        }
    }

    @MultiEngineTest
    fun testQuery(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val entries = mutableListOf<String>()

            for (i in 0 until r.header.query.count) {
                val entry = r.header.query.getStringEntry(i)

                val key = entry.first.toString()
                val value = entry.second.toString()

                entries.add("$key=$value")
            }

            val result = entries.joinToString("|")
            r.respond().content(result).build()
        }.use { setup ->
            val runner = setup.runner

            val r2 = runner.getResponse("/?one=two")
            assertEquals("one=two", r2.getContent())

            val r3 = runner.getResponse("/?one=two&three=four")
            assertEquals("one=two|three=four", r3.getContent())

            val r1 = runner.getResponse("/")
            assertEquals("", r1.getContent())
        }
    }

    @MultiEngineTest
    fun testQuerySpecialChars(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val entries = mutableListOf<String>()

            for (i in 0 until r.header.query.count) {
                val entry = r.header.query.getStringEntry(i)

                val key = entry.first.toString()
                val value = URLDecoder.decode(entry.second.toString(), Charsets.UTF_8)

                entries.add("$key=$value")
            }

            val result = entries.joinToString("|")

            r.respond().content(result).build()
        }.use { setup ->
            val runner = setup.runner

            val r = runner.getResponse("/?key=%20%3C+")
            assertEquals("key= < ", r.getContent())
        }
    }

    @MultiEngineTest
    fun testPathSpecialChars(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond().content(r.header.target.asString(decode = false)).build()
        }.use { setup ->
            val runner = setup.runner

            // upstream requests "/%3F%23%26%2F %20", the space is sent as %20 by the .NET client (the JDK
            // client rejects the URI), so the bytes on the wire are identical
            val r = runner.getResponse("/%3F%23%26%2F%20%20")
            assertEquals("/%3F%23%26%2F%20%20", r.getContent())
        }
    }

    @MultiEngineTest
    fun testPathPreservesSpecialChars(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond().content(r.header.target.asString()).build()
        }.use { setup ->
            val runner = setup.runner

            val r = runner.getResponse("/$@:")
            assertEquals("/$@:", r.getContent())
        }
    }

    @MultiEngineTest
    fun testContentLengthPreserved(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond()
                .content("Hello World", ContentType.ImageJpeg)
                .build()
        }.use { setup ->
            val response = setup.runner.getResponse()

            assertEquals(11L, response.getContentHeader("Content-Length")?.toLong())
            AssertX.isNullOrEmpty(response.getHeader("Transfer-Encoding"))
        }
    }

    @MultiEngineTest
    fun testBadGateway(engine: ServerEngine) = test {
        val proxy = Proxy.create()
            .upstream("http://icertainlydonotexistasadomain")

        TestHost.run(proxy, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.BadGateway)
        }
    }

    @MultiEngineTest
    fun testCompression(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val responseContent = "B".repeat(2048)
            r.respond().content(responseContent).build()
        }.use { setup ->
            val runner = setup.runner

            val request = runner.getRequest()

            request.header("Accept-Encoding", "br, gzip, deflate")

            val response = runner.getResponse(request)

            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    @MultiEngineTest
    fun testRedirectionToExternalHostIsNotRewritten(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            r.respond().header("Location", "https://example.com/elsewhere").status(ResponseStatus.TemporaryRedirect).build()
        }.use { setup ->
            val redirected = setup.runner.getResponse("/")

            assertEquals("https://example.com/elsewhere", redirected.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testRedirectionFromScopedMount(engine: ServerEngine) = test {
        TestHost(Layout.create().build(), false, engine = engine).use { upstream ->
            upstream.host.handler(
                ProxiedRouter { r ->
                    r.respond().header("Location", "http://localhost:${upstream.port}/target").status(ResponseStatus.TemporaryRedirect).build()
                }
            ).start()

            val proxy = Proxy.create().upstream("http://localhost:" + upstream.port)

            TestHost.run(Layout.create().add("api", proxy), engine = engine).use { runner ->
                val redirected = runner.getResponse("/api/whatever")

                val location = redirected.getHeader("Location")

                assertNotNull(location)
                AssertX.contains("http://localhost:${runner.port}", location)
                AssertX.contains("/target", location)
            }
        }
    }

    @MultiEngineTest
    fun testForwardingByAddressIsRelayed(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val header = r.header.headers.getEntry("Forwarded")

            assertNotNull(header)
            AssertX.contains("by=203.0.113.5", header)

            r.respond().content("Hello World!").build()
        }.use { setup ->
            val request = setup.runner.getRequest()
            request.header("Forwarded", "for=85.192.1.5; by=203.0.113.5; host=google.com")

            val response = setup.runner.getResponse(request)
            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testForwardingByAddressIsRelayedForIPv6(engine: ServerEngine) = test {
        TestSetup.create(engine) { r ->
            val header = r.header.headers.getEntry("Forwarded")

            assertNotNull(header)
            AssertX.contains("by=[2001:db8::1]", header)

            r.respond().content("Hello World!").build()
        }.use { setup ->
            val request = setup.runner.getRequest()
            request.header("Forwarded", "for=85.192.1.5; by=\"[2001:db8::1]\"; host=google.com")

            val response = setup.runner.getResponse(request)
            assertEquals("Hello World!", response.getContent())
        }
    }

    @Test
    fun testChaining() {
        Chain.works(Proxy.create().upstream("https://google.com"))
    }

    @Test
    fun testAdjustments() {
        var i = 0

        val proxy = Proxy.create()
            .upstream("https://google.com")
            .adjustHandler { i++ }
            .adjustClient { i++ }

        proxy.build()

        assertEquals(2, i)
    }

    // region Supporting data structures

    private class TestSetup private constructor(val runner: TestHost, private val target: TestHost) : AutoCloseable {

        companion object {

            suspend fun create(engine: ServerEngine, response: (Request) -> Response?): TestSetup {
                // server hosting the actual web app
                val testServer = TestHost(Layout.create().build(), false, engine = engine)

                testServer.host.handler(ProxiedRouter(response))
                    .start()

                // proxying server
                val proxy = Proxy.create()
                    .connectTimeout(2.seconds)
                    .readTimeout(5.seconds)
                    .upstream("http://localhost:" + testServer.port)

                val runner = TestHost(Layout.create().build(), engine = engine)

                runner.host.handler(proxy)
                    .start()

                return TestSetup(runner, testServer)
            }

        }

        override fun close() {
            runner.close()
            target.close()
        }

    }

    private class ProxiedRouter(private val response: (Request) -> Response?) : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? = ProxiedProvider(response).handle(request)

    }

    private class ProxiedProvider(private val response: (Request) -> Response?) : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            assertNotNull(request.client.address)

            val response = response.invoke(request)

            if (response != null) {
                return response
            }

            return request.respond()
                .status(ResponseStatus.InternalServerError)
                .build()
        }

    }

    // endregion

}
