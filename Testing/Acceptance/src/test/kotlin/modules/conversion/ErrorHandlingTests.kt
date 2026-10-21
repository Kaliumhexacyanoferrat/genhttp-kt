package org.genhttp.testing.acceptance.modules.conversion

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.post
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import java.net.http.HttpRequest.BodyPublishers

class ErrorHandlingTests {

    // region Tests

    @MultiEngineTest
    fun undeserializableBodyReturnsWithBadRequest(engine: ServerEngine) = test {
        val inline = Inline.create()
            .post("/t") { entity: MyEntity -> entity.data }

        TestHost.run(inline, engine = engine).use { runner ->
            val request = runner.getRequest("/t")
                .method("POST", BodyPublishers.ofString("I cannot be deserialized"))
                .header("Content-Type", "application/json")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    // endregion

    // region Supporting data structures

    data class MyEntity(val data: String)

    // endregion

}
