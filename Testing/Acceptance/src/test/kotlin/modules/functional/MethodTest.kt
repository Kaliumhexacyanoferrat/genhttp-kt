package org.genhttp.testing.acceptance.modules.functional

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals

class MethodTest {

    @MultiEngineFrameworkTest
    fun testAnyMethod(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create()
            .any { data: List<Int> -> data.size }
            .executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            for (method in listOf("POST", "PUT")) {
                val request = host.getRequest(method = method)
                    .method(method, BodyPublishers.ofString("[ 1, 2 ]"))
                    .header("Content-Type", "application/json")

                val response = host.getResponse(request)

                response.assertStatus(ResponseStatus.Ok)

                assertEquals("2", response.getContent())
            }
        }
    }

    @MultiEngineFrameworkTest
    fun testDelete(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create()
            .delete { }
            .executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val request = host.getRequest(method = "DELETE")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineFrameworkTest
    fun testHead(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Inline.create()
            .head { "42" }
            .executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val request = host.getRequest(method = "HEAD")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("2", response.getContentHeader("Content-Length"))
        }
    }

}
