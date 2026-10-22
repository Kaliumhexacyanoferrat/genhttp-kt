@file:JvmName("Extensions")

package org.genhttp.modules.authentication

import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request

import kotlin.reflect.KClass

// the name of the request property the authenticated user is stored in
private const val UserProperty: String = "__AUTH_USER"

/**
 * Sets the user that has been authenticated for the given request.
 *
 * @param user The user to be associated with the request
 */
public fun Request.setUser(user: User) {
    properties[UserProperty] = user
}

/**
 * Fetches the user of the given type that has been authenticated for the request.
 *
 * @param type The type of user to be fetched
 * @return The authenticated user or null, if there is no user or if the user is not of the requested type
 */
public fun <T : User> Request.getUser(type: KClass<T>): T? = properties.tryGet(UserProperty, type)

/**
 * Fetches the user of the given type that has been authenticated for the request.
 *
 * @return The authenticated user or null, if there is no user or if the user is not of the requested type
 */
public inline fun <reified T : User> Request.getUser(): T? = getUser(T::class)

/**
 * Removes the authenticated user from the request.
 */
public fun Request.clearUser() {
    properties.clear(UserProperty)
}
