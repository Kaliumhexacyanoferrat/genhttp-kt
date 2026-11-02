package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.directorybrowsing.Listing
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.fromDirectory
import org.genhttp.modules.singlepageapplications.SinglePageApplication
import org.genhttp.modules.staticwebsites.StaticWebsite
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.net.Socket
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Verifies that every file serving handler rejects path traversal attempts
 * via [org.genhttp.modules.io.denyPathTraversal].
 *
 * Each handler is probed with a different forged segment so that, taken
 * together, the tests exercise all branches of the detection logic (plain
 * dots, upper- and lower-case percent encoding, mixed forms and the negative
 * cases that must not be denied).
 */
class PathTraversalTests {

    // region Denial (one case per affected handler)

    @MultiEngineTest
    fun testAssetsTreeDeniesEncodedTraversal(engine: ServerEngine) = test {
        // TreeAssetsHandler (via AbstractAssetsHandler) - percent encoded ".." ("%2e%2e")
        TestHost.run(Assets.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val response = getForged(runner, "/sub/%2e%2e/file.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testAssetsDirectoryDeniesLiteralTraversal(engine: ServerEngine) = test {
        // BuiltInFileAssetHandler (via AbstractAssetsHandler) - literal ".."
        TestHost.run(Assets.from(createRoot()), engine = engine).use { runner ->
            val response = getForged(runner, "/sub/../file.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testStaticWebsiteDeniesUppercaseEncodedTraversal(engine: ServerEngine) = test {
        // StaticWebsiteHandler trailing slash branch (bypasses AbstractAssetsHandler) - upper case "%2E"
        TestHost.run(StaticWebsite.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val response = getForged(runner, "/sub/%2E/")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testSinglePageDeniesMixedTraversal(engine: ServerEngine) = test {
        // SinglePageProvider - mixed literal and encoded dot (".%2e")
        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val response = getForged(runner, "/app/.%2e/config.json")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testDirectoryBrowsingDeniesSingleDot(engine: ServerEngine) = test {
        // ListingRouter (bypasses AbstractAssetsHandler) - single "."
        TestHost.run(Listing.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val response = getForged(runner, "/sub/./")

            assertStatus(response, 400)
        }
    }

    // endregion

    // region Non-traversal segments (completes detection coverage)

    @MultiEngineTest
    fun testSuspiciousButValidSegmentsAreAllowed(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            // a genuine file is still served (first byte is not a dot - detection returns immediately)
            val served = getForged(runner, "/file.txt")
            assertStatus(served, 200)

            // the following segments look suspicious but are not "." or ".." - the guard must let them
            // through (their resolved status is filesystem dependent, so we only assert they are not denied):

            // three dots - the dot count is neither 1 nor 2
            val tripleDot = getForged(runner, "/.../file.txt")
            assertNotEquals(400, tripleDot.statusCode)

            // percent escape whose first digit is not '2' ("%41" = 'A')
            val notTwo = getForged(runner, "/%41/file.txt")
            assertNotEquals(400, notTwo.statusCode)

            // percent escape "%2x" where x is neither 'e' nor 'E' ("%2b" = '+')
            val notDot = getForged(runner, "/%2b/file.txt")
            assertNotEquals(400, notDot.statusCode)

            // trailing, incomplete percent escape - not enough characters to form "%2e"
            val incomplete = getForged(runner, "/.%/file.txt")
            assertNotEquals(400, incomplete.statusCode)
        }
    }

    // endregion

    // region Encoded separators

    @MultiEngineTest
    fun testAssetsDirectoryDeniesEncodedSlash(engine: ServerEngine) = test {
        TestHost.run(Assets.from(createJail()), engine = engine).use { runner ->
            val response = getForged(runner, "/..%2fsecret.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testAssetsTreeDeniesFullyEncodedTraversal(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromDirectory(createJail())), engine = engine).use { runner ->
            val response = getForged(runner, "/%2e%2e%2Fsecret.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testStaticWebsiteDeniesEncodedBackslash(engine: ServerEngine) = test {
        TestHost.run(StaticWebsite.from(ResourceTree.fromDirectory(createJail())), engine = engine).use { runner ->
            val response = getForged(runner, "/..%5Csecret.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testAssetsDeniesLiteralBackslash(engine: ServerEngine) = test {
        TestHost.run(Assets.from(createJail()), engine = engine).use { runner ->
            val response = getForged(runner, "/sub\\file.txt")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testSinglePageDeniesNullByte(engine: ServerEngine) = test {
        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(createJail())), engine = engine).use { runner ->
            val response = getForged(runner, "/file.txt%00.html")

            assertStatus(response, 400)
        }
    }

    @MultiEngineTest
    fun testListingDoesNotFollowDriveRelativeSegment(engine: ServerEngine) = test {
        TestHost.run(Listing.from(ResourceTree.fromDirectory(createJail())), engine = engine).use { runner ->
            val response = getForged(runner, "/C:/")

            assertStatus(response, 404)
        }
    }

    // endregion

    // region Directory tree containment

    @Test
    fun testTreeResolvesDirectChildren() = test {
        val tree = ResourceTree.fromDirectory(createJail()).build()

        assertNotNull(tree.tryGetResource(PathSegment("file.txt")))
        assertNotNull(tree.tryGetNode(PathSegment("sub")))
    }

    @Test
    fun testTreeDoesNotResolveOutsideOfRoot() = test {
        val root = createJail()

        val tree = ResourceTree.fromDirectory(root).build()

        val secret = Path.of(root).parent.resolve("secret.txt").toString()

        assertNull(tree.tryGetResource(PathSegment("..%2fsecret.txt")))
        assertNull(tree.tryGetResource(PathSegment("..%5csecret.txt")))
        assertNull(tree.tryGetResource(PathSegment(URLEncoder.encode(secret, Charsets.UTF_8))))

        assertNull(tree.tryGetNode(PathSegment("..")))
        assertNull(tree.tryGetNode(PathSegment(".")))
        assertNull(tree.tryGetNode(PathSegment("sub%2f..%2f..")))
        assertNull(tree.tryGetNode(PathSegment("C:")))
    }

    // endregion

    // region Helpers

    private fun createRoot(): String {
        val root = Files.createTempDirectory("genhttp")

        Files.writeString(root.resolve("file.txt"), "This is a file")

        Files.createDirectory(root.resolve("sub"))

        return root.toAbsolutePath().toString()
    }

    /**
     * Creates a served root with a secret file placed next to it, which
     * must never be reachable through the root.
     */
    private fun createJail(): String {
        val outer = Files.createTempDirectory("genhttp")

        Files.writeString(outer.resolve("secret.txt"), "This is a secret")

        val root = Files.createDirectory(outer.resolve("www"))

        Files.writeString(root.resolve("file.txt"), "This is a file")

        Files.createDirectory(root.resolve("sub"))

        return root.toAbsolutePath().toString()
    }

    /**
     * The status and body of a response to a forged request.
     */
    private class ForgedResponse(val statusCode: Int, val body: String)

    /**
     * Sends a request without client side canonicalization, so that forged
     * segments such as ".." or "%2e%2e" actually reach the server instead of
     * being collapsed (or rejected) by the URI handling of the HTTP client.
     */
    private fun getForged(runner: TestHost, rawPath: String): ForgedResponse {
        Socket("localhost", runner.port).use { socket ->
            socket.soTimeout = Constants.ClientTimeout

            socket.getOutputStream().apply {
                write("GET $rawPath HTTP/1.1\r\nHost: localhost:${runner.port}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }

            val response = String(socket.getInputStream().readAllBytes(), Charsets.ISO_8859_1)

            val statusLine = response.substringBefore("\r\n")

            val parts = statusLine.split(' ')

            if (parts.size < 2) {
                fail("Server did not return a valid status line: '$statusLine'")
            }

            return ForgedResponse(parts[1].toInt(), response.substringAfter("\r\n\r\n", ""))
        }
    }

    private fun assertStatus(response: ForgedResponse, expectedStatus: Int) {
        assertEquals(expectedStatus, response.statusCode, "Response returned with status '${response.statusCode}', expected '$expectedStatus'.\r\n\r\n${response.body}")
    }

    // endregion

}
