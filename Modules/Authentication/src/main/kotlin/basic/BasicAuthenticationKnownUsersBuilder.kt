package org.genhttp.modules.authentication.basic

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.modules.authentication.AuthenticationBuilder
import org.genhttp.modules.authentication.documentation.SchemeDocumentation

import java.security.MessageDigest
import java.util.TreeMap

/**
 * Configures a concern that checks credentials sent via basic authentication against a list of users
 * kept in memory (for development purposes).
 */
public class BasicAuthenticationKnownUsersBuilder : AuthenticationBuilder<BasicAuthenticationKnownUsersBuilder> {

    private val schemeDocumentation = SchemeDocumentation()

    private val users = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)

    private var realm: String? = null

    // region Functionality

    /**
     * Sets the name of the realm returned to the client.
     *
     * @param realm The name of the realm
     */
    public fun realm(realm: String): BasicAuthenticationKnownUsersBuilder {
        this.realm = realm
        return this
    }

    /**
     * Registers a user that is allowed to access the restricted area (user names are case-insensitive).
     *
     * @param user The name of the user
     * @param password The password of the user
     * @throws IllegalArgumentException Thrown if a user with the same name has already been added
     */
    public fun add(user: String, password: String): BasicAuthenticationKnownUsersBuilder {
        require(!users.containsKey(user)) { "An item with the same key has already been added. Key: $user" }

        users[user] = password
        return this
    }

    override fun documentation(description: String): BasicAuthenticationKnownUsersBuilder {
        schemeDocumentation.description = description
        return this
    }

    override fun documentation(scheme: SecurityScheme): BasicAuthenticationKnownUsersBuilder {
        schemeDocumentation.scheme = scheme
        return this
    }

    override fun build(content: Handler): Concern {
        val realm = realm ?: throw BuilderMissingPropertyException("Realm")

        val authenticator: suspend (String, String) -> User? = { user, password ->
            val expected = users[user]

            if (expected != null && MessageDigest.isEqual(password.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))) {
                BasicAuthenticationUser(user)
            } else {
                null
            }
        }

        return BasicAuthenticationConcern(content, realm, authenticator, schemeDocumentation.resolve(SecurityScheme.basic()))
    }

    // endregion

}
