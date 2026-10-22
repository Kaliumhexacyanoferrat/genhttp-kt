package org.genhttp.modules.authentication.apikey

import org.genhttp.api.content.authentication.User

/**
 * A user that has been authenticated by an API key.
 *
 * @param key The API key the user authenticated with
 * @param roles The roles of the user
 */
public class ApiKeyUser(public val key: String, vararg roles: String) : User {

    override val displayName: String
        get() = key

    override val roles: List<String> = roles.toList()

}
