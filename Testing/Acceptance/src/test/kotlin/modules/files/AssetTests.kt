package org.genhttp.testing.acceptance.modules.files

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Asset
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals

class AssetTests {

    @MultiEngineTest
    fun testDownload(engine: ServerEngine) = test {
        TestHost.run(Asset.from(Resource.fromClasspath("Resources/File.txt")), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is text!", response.getContent())
            assertEquals("text/plain", response.getContentHeader("Content-Type"))
        }
    }

    @MultiEngineTest
    fun testDownloadDoesNotAcceptRouting(engine: ServerEngine) = test {
        val layout = Layout.create()
            .add("file.txt", Asset.from(Resource.fromClasspath("Resources/File.txt")))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse("/file.txt/blubb")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun downloadsCannotBeModified(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromClasspath("Resources/File.txt"))

        TestHost.run(download, engine = engine).use { runner ->
            val request = runner.getRequest()

            request.method("PUT", BodyPublishers.ofString("Hello World!"))
            request.header("Content-Type", "text/plain; charset=utf-8")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.MethodNotAllowed)

            assertEquals("GET", response.getContentHeader("Allow"))
        }
    }

    @MultiEngineTest
    fun testFileName(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromClasspath("Resources/File.txt"))
            .asDownload("myfile.txt")

        TestHost.run(download, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("attachment; filename=\"myfile.txt\"", response.getContentHeader("Content-Disposition"))
        }
    }

    @MultiEngineTest
    fun testNoFileName(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromClasspath("Resources/File.txt"))
            .asDownload()

        TestHost.run(download, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("attachment", response.getContentHeader("Content-Disposition"))
        }
    }

    @MultiEngineTest
    fun testFileNameFromResource(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromClasspath("Resources/File.txt").name("myfile.txt"))
            .asDownload()

        TestHost.run(download, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("attachment; filename=\"myfile.txt\"", response.getContentHeader("Content-Disposition"))
        }
    }

    @MultiEngineTest
    fun testTypeCanBeSet(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromString("This;is;CSV"))
            .type(ContentType.TextCsv)

        TestHost.run(download, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("text/csv", response.getContentHeader("Content-Type"))
        }
    }

}
