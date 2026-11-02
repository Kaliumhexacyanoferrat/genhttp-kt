package org.genhttp.testing.acceptance.modules.directorybrowsing

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.directorybrowsing.Listing
import org.genhttp.modules.io.VirtualTree
import org.genhttp.modules.io.fromString
import org.genhttp.modules.pages.Renderer
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

class ListingInjectionTests {

    @MultiEngineTest
    fun testUnknownIntermediateSegmentIsNotFound(engine: ServerEngine) = test {
        val tree = VirtualTree.create()
            .add("sub", VirtualTree.create())

        TestHost.run(Listing.from(tree), engine = engine).use { runner ->
            val response = runner.getResponse("/%3Cimg%20src%3Dx%20onerror%3Dalert(1)%3E/x")

            response.assertStatus(ResponseStatus.NotFound)

            AssertX.doesNotContain("<img", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNamesAreEscaped(engine: ServerEngine) = test {
        val imageName = "<img src=x onerror=alert(1)>.txt"
        val attributeName = "x\" onmouseover=\"alert(2).txt"

        val tree = VirtualTree.create()
            .add(imageName, Resource.fromString("1").name(imageName))
            .add(attributeName, Resource.fromString("2").name(attributeName))
            .add("<b>dir</b>", VirtualTree.create())

        TestHost.run(Listing.from(tree), engine = engine).use { runner ->
            val response = runner.getResponse("/")

            response.assertStatus(ResponseStatus.Ok)

            val content = response.getContent()

            AssertX.doesNotContain("<img", content)
            AssertX.doesNotContain("<b>", content)
            AssertX.doesNotContain("\" onmouseover", content)

            AssertX.contains("&lt;img src=x onerror=alert(1)&gt;.txt", content)
            AssertX.contains("href=\"./%3Cimg%20src%3Dx%20onerror%3Dalert%281%29%3E.txt\"", content)
            AssertX.contains("href=\"./x%22%20onmouseover%3D%22alert%282%29.txt\"", content)
            AssertX.contains("href=\"./%3Cb%3Edir%3C%2Fb%3E/\"", content)
        }
    }

    @Test
    fun testServerPageEscapesTitle() = test {
        val page = Renderer.server.render("<script>alert(1)</script>", "<p>Content</p>")

        AssertX.doesNotContain("<script>", page)
        AssertX.contains("&lt;script&gt;alert(1)&lt;/script&gt;", page)
        AssertX.contains("<p>Content</p>", page)
    }

}
