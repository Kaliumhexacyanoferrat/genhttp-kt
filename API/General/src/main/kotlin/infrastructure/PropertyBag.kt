package org.genhttp.api.infrastructure

import kotlin.reflect.KClass

/**
 * Property bag to store additional data within a context, such as a request or server instance.
 */
public interface PropertyBag {

    /**
     * Accesses a value that is stored within the property bag.
     *
     * @param key The key of the item to be fetched
     * @throws NoSuchElementException Thrown if the given key is not present
     */
    public operator fun get(key: String): Any

    /**
     * Stores a value within the property bag.
     *
     * @param key The key of the item to be stored
     * @param value The value to be stored
     */
    public operator fun set(key: String, value: Any)

    /**
     * Attempts to fetch the typed value for the given key from the property bag.
     *
     * @param key The key of the value to be fetched
     * @param type The expected type of value to be returned
     * @return The entry read from the property bag or null, if there is no entry of the given type
     */
    public fun <T : Any> tryGet(key: String, type: KClass<T>): T?

    /**
     * Removes the entry with the given name.
     *
     * @param key The entry to be removed
     */
    public fun clear(key: String)

}

/**
 * Attempts to fetch the typed value for the given key from the property bag.
 *
 * @param key The key of the value to be fetched
 * @return The entry read from the property bag or null, if there is no entry of the given type
 */
public inline fun <reified T : Any> PropertyBag.tryGet(key: String): T? = tryGet(key, T::class)
