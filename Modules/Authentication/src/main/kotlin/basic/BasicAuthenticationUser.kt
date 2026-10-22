package org.genhttp.modules.authentication.basic

import org.genhttp.api.content.authentication.User

/**
 * A user that has been authenticated by basic authentication.
 *
 * @property name The name of the user
 * @property roles The roles of the user
 */
public data class BasicAuthenticationUser(val name: String, override val roles: List<String>) : User {

    /**
     * Creates a user with the given roles.
     *
     * @param name The name of the user
     * @param roles The roles of the user
     */
    public constructor(name: String, vararg roles: String) : this(name, roles.toList())

    override val displayName: String
        get() = name

}
