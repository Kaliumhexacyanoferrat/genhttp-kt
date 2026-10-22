package org.genhttp.modules.authentication.basic

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Configures a concern that checks credentials sent via basic authentication.
 */
public class BasicAuthenticationConcernBuilder : ConcernBuilder {

    private var handler: (suspend (String, String) -> User?)? = null

    private var realm: String? = null

    // region Functionality

    /**
     * Sets the name of the realm returned to the client.
     *
     * @param realm The name of the realm
     */
    public fun realm(realm: String): BasicAuthenticationConcernBuilder {
        this.realm = realm
        return this
    }

    /**
     * Sets the logic used to check the credentials passed by the client.
     *
     * @param handler The logic to be evaluated (receives the user name and the password)
     */
    public fun handler(handler: suspend (String, String) -> User?): BasicAuthenticationConcernBuilder {
        this.handler = handler
        return this
    }

    override fun build(content: Handler): Concern {
        val realm = realm ?: throw BuilderMissingPropertyException("Realm")

        val handler = handler ?: throw BuilderMissingPropertyException("Handler")

        return BasicAuthenticationConcern(content, realm, handler)
    }

    // endregion

}
