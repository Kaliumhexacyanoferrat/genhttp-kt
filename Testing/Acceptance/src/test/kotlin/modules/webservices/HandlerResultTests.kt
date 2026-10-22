package org.genhttp.testing.acceptance.modules.webservices

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.VirtualTree
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.staticwebsites.StaticWebsite
import org.genhttp.modules.staticwebsites.from
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class HandlerResultTests {

    // region Helpers

    companion object {

        fun createTree(): Builder<ResourceTree> {
            val subTree = VirtualTree.create()
                .add("index.htm", Resource.fromString("Sub Index"))
                .add("my.txt", Resource.fromString("My Textfile"))

            return VirtualTree.create()
                .add("index.html", Resource.fromString("Index"))
                .add("sub", subTree)
        }

    }

    // endregion

    // region Supporting data structures

    class RootService {

        companion object {
            private val Tree = createTree()
        }

        @ResourceMethod
        fun root(): HandlerBuilder = StaticWebsite.from(Tree)

    }

    class PathService {

        companion object {
            private val Tree = createTree()
        }

        @ResourceMethod(path = "/mypath/:pathParam/")
        fun pathed(pathParam: String): HandlerBuilder {
            assertEquals("param", pathParam)

            return StaticWebsite.from(Tree)
        }

    }

    class PathAsyncService {

        companion object {
            private val Tree = createTree()
        }

        @ResourceMethod(path = "/mypath/:pathParam/")
        suspend fun pathed(pathParam: String): HandlerBuilder {
            assertEquals("param", pathParam)

            val handlerBuilder: HandlerBuilder = StaticWebsite.from(Tree)

            return handlerBuilder
        }

    }

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testRoot(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Layout.create()
            .addService<RootService>("c", mode = mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/c/sub/my.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("My Textfile", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testPathed(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Layout.create()
            .addService<PathService>("c", mode = mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/c/mypath/param/sub/my.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("My Textfile", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testPathedAsync(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Layout.create()
            .addService<PathAsyncService>("c", mode = mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/c/mypath/param/sub/my.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("My Textfile", response.getContent())
        }
    }

    // endregion

}
