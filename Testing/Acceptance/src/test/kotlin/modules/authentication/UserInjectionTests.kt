package org.genhttp.testing.acceptance.modules.authentication

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.BasicAuthentication
import org.genhttp.modules.authentication.authentication
import org.genhttp.modules.authentication.basic.BasicAuthenticationUser
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.injectors.UserInjector
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.PasswordAuthentication

import kotlin.test.assertEquals

class UserInjectionTests {

    // region Helpers

    private suspend fun getRunner(engine: ServerEngine): TestHost {
        val auth = BasicAuthentication.create()
            .add("abc", "def")

        val injection = Injection.empty()
            .add(UserInjector(BasicAuthenticationUser::class))

        val content = Inline.create()
            .get { user: BasicAuthenticationUser -> user.displayName }
            .injectors(injection)
            .authentication(auth)

        return TestHost.run(content, engine = engine)
    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testUserInjected(engine: ServerEngine) = test {
        getRunner(engine).use { runner ->
            val client = TestHost.getClient(creds = PasswordAuthentication("abc", "def".toCharArray()))

            val response = runner.getResponse(client = client)

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("abc", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNoUser(engine: ServerEngine) = test {
        getRunner(engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    // endregion

}
