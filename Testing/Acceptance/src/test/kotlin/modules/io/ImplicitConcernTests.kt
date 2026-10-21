package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.Concern
import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class ImplicitConcernTests {

    @Test
    fun testConcern() = test {
        val app = Content.from(Resource.fromString("Hello World"))
            .add(Concern.from { r, c ->
                val response = c.handle(r)

                response?.rebuild()?.header("X-Custom", "Value")

                response
            })

        TestHost.run(app).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())

            assertEquals("Value", response.getHeader("X-Custom"))
        }
    }

}
