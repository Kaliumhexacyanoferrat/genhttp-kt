package org.genhttp.testing.acceptance.modules.authentication

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.RequireRole
import org.genhttp.modules.authentication.setUser
import org.genhttp.modules.functional.Inline
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import java.net.http.HttpResponse

class RoleTests {

    // region Supporting data structures

    class UserSettingConcernBuilder(private val user: User?) : ConcernBuilder {

        override fun build(content: Handler): Concern = UserSettingConcern(content, user)

    }

    class UserSettingConcern(override val content: Handler, val user: User?) : Concern {

        override suspend fun prepare(server: Server) = content.prepare(server)

        override suspend fun handle(request: Request): Response? {
            if (user != null) {
                request.setUser(user)
            }

            return content.handle(request)
        }

    }

    class RoleUser(override val roles: List<String>?) : User {

        override val displayName: String
            get() = "Role User"

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testNoUser(engine: ServerEngine) = test {
        val response = run(null, engine)

        response.assertStatus(ResponseStatus.Unauthorized)
    }

    @MultiEngineTest
    fun testNoRoles(engine: ServerEngine) = test {
        val response = run(RoleUser(null), engine)

        response.assertStatus(ResponseStatus.Forbidden)
    }

    @MultiEngineTest
    fun testInsufficientRoles(engine: ServerEngine) = test {
        val response = run(RoleUser(listOf("ADMIN")), engine)

        response.assertStatus(ResponseStatus.Forbidden)
    }

    @MultiEngineTest
    fun testSufficientRoles(engine: ServerEngine) = test {
        val response = run(RoleUser(listOf("ADMIN", "SUPER_ADMIN")), engine)

        response.assertStatus(ResponseStatus.Ok)
    }

    @MultiEngineTest
    fun testCasingDoesNotMatter(engine: ServerEngine) = test {
        val response = run(RoleUser(listOf("admin", "Super_Admin")), engine)

        response.assertStatus(ResponseStatus.Ok)
    }


    @MultiEngineTest
    fun testOtherRolesDoNotMatter(engine: ServerEngine) = test {
        val response = run(RoleUser(listOf("ADMIN", "USER", "SUPER_ADMIN")), engine)

        response.assertStatus(ResponseStatus.Ok)
    }

    // endregion

    // region Helpers

    private suspend fun run(user: User?, engine: ServerEngine): HttpResponse<ByteArray> {
        val app = Inline.create()
            .get(::secured)
            .add(UserSettingConcernBuilder(user))

        TestHost.run(app, engine = engine).use { host ->
            return host.getResponse()
        }
    }

    // endregion

}

// an annotation cannot be placed on a lambda in Kotlin, so the handler is a function reference
@RequireRole("ADMIN", "SUPER_ADMIN")
suspend fun secured(): Int = 42
