package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.io.BodyArguments
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.URLEncoder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

import kotlin.test.assertEquals

class BodyArgumentsTests {

    @MultiEngineFrameworkTest
    fun testArgumentsAreInjected(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create()
            .post { args: BodyArguments -> "${args.getEntry("name")}-${args.getEntry("age")}" }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val form = mapOf("name" to "John Doe", "age" to "42")
                .entries.joinToString("&") { "${URLEncoder.encode(it.key, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }

            val response = post(host, form)

            assertEquals("John Doe-42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testEncodedCharactersAreDecoded(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create()
            .post { args: BodyArguments -> args.getEntry("value") }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = post(host, "value=a%2Bb%3Dc")

            assertEquals("a+b=c", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testNoBody(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create()
            .get { args: BodyArguments -> args.count }
            .executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("0", response.getContent())
        }
    }

    private suspend fun post(host: TestHost, body: String): HttpResponse<ByteArray> {
        val request = host.getRequest(method = "POST")
            .method("POST", BodyPublishers.ofString(body))
            .header("Content-Type", "application/x-www-form-urlencoded")

        return host.getResponse(request)
    }

}
