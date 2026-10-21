package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.io.buildWithTracking
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.fromFile
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.streaming.ResourceContent
import org.genhttp.modules.io.track
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FileUtil
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.nio.file.Files

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeTrackingTests {

    @Test
    fun testChanges() = test {
        val file = Files.createTempFile("genhttp", ".tmp")

        try {
            FileUtil.writeTextAsync(file, "One")

            val resource = Resource.fromFile(file)
                .build()
                .track()

            resource.getContent().use { }

            assertFalse(resource.checkChanged())

            // modification timestamp is in seconds on unix, so we need another length
            FileUtil.writeTextAsync(file, "Three")

            assertTrue(resource.checkChanged())
        } finally {
            try {
                Files.deleteIfExists(file)
            } catch (_: Exception) { /* nop */
            }
        }
    }

    @Test
    fun testBuildWithTracking() = test {
        assertTrue(Resource.fromClasspath("Resources/File.txt").buildWithTracking().checkChanged())
    }

    @Test
    fun testMetaInformation() {
        val resource = Resource.fromClasspath("Resources/File.txt").build()

        val tracked = resource.track()

        assertEquals(resource.name, tracked.name)
        assertEquals(resource.length, tracked.length)
        assertEquals(resource.modified, tracked.modified)
        assertEquals(resource.contentType, tracked.contentType)
    }

    @MultiEngineTest
    fun testContentExposure(engine: ServerEngine) = test {
        val resource = Resource.fromString("Inner").buildWithTracking()

        val app = Inline.create()
            .get { r: Request -> r.respond().content(ResourceContent(resource)).build() }

        TestHost.run(app).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Inner", response.getContent())
        }
    }

}
