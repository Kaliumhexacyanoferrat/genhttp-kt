package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.net.URLDecoder

import kotlin.test.assertEquals

class ParserTests {

    // the JDK client requires URIs to be encoded, .NET encodes the request URI on its own

    @MultiEngineTest
    fun testEndodedUri(engine: ServerEngine) = test {
        TestHost.run(PathReturner().wrap(), engine = engine).use { runner ->
            val respose = runner.getResponse("/s%C3%B6me/%C3%BCrl/with%20specialities/")

            assertEquals("/söme/ürl/with specialities/", respose.getContent())
        }
    }

    @MultiEngineTest
    fun testEncodedQuery(engine: ServerEngine) = test {
        TestHost.run(QueryReturner().wrap(), engine = engine).use { runner ->
            val respose = runner.getResponse("/?s%C3%B6me%20key=%F0%9F%92%95")

            assertEquals("söme key=💕", respose.getContent())
        }
    }

    @Test
    fun testMultipleSlashes() = test {
        TestHost.run(PathReturner().wrap()).use { runner ->
            val respose = runner.getResponse("//one//two//three//")

            assertEquals("//one//two//three//", respose.getContent())
        }
    }

    @MultiEngineTest
    fun testEmptyQuery(engine: ServerEngine) = test {
        TestHost.run(QueryReturner().wrap(), engine = engine).use { runner ->
            val respose = runner.getResponse("/?")

            assertEquals("", respose.getContent())
        }
    }

    @MultiEngineTest
    fun testUnkeyedQuery(engine: ServerEngine) = test {
        TestHost.run(QueryReturner().wrap(), engine = engine).use { runner ->
            val respose = runner.getResponse("/?query")

            assertEquals("query=", respose.getContent())
        }
    }

    @MultiEngineTest
    fun testQueryWithSlashes(engine: ServerEngine) = test {
        TestHost.run(QueryReturner().wrap(), engine = engine).use { runner ->
            val respose = runner.getResponse("/?key=/one/two")

            assertEquals("key=/one/two", respose.getContent())
        }
    }

    @Test
    fun testQueryWithSpaces() = test {
        TestHost.run(QueryReturner().wrap()).use { runner ->
            val respose = runner.getResponse("/?path=/Some+Folder/With%20Subfolders/")

            assertEquals("path=/Some Folder/With Subfolders/", respose.getContent())
        }
    }

    // region Supporting data structures

    private class PathReturner : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? =
            request.respond()
                .content(request.header.target.asString())
                .build()

    }

    private class QueryReturner : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            val entries = mutableListOf<String>()

            for (i in 0 until request.header.query.count) {
                val entry = request.header.query.getStringEntry(i)

                val key = URLDecoder.decode(entry.first.toString(), Charsets.UTF_8)
                val value = URLDecoder.decode(entry.second.toString(), Charsets.UTF_8)

                entries.add("$key=$value")
            }

            val result = entries.joinToString("|")

            return request.respond()
                .content(result)
                .build()
        }

    }

    // endregion

}
