package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class ImplicitHandlerTests {

    @Test
    fun testHandlerAsync() = test {
        val handler = Handler.from { r -> Content.from(Resource.fromString("Hello World")).build().handle(r) }

        TestHost.run(handler).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @Test
    fun testHandlerSync() = test {
        val handler = Handler.from { r ->
            r.respond()
                .content(StringContent("Hello World"))
                .build()
        }

        TestHost.run(handler).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @Test
    fun testConcernChaining() {
        Chain.works(Handler.from { r -> r.respond().content(StringContent("Hello World")).build() })
    }

}
