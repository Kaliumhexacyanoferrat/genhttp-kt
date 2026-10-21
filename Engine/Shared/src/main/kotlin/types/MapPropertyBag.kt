package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.PropertyBag

import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

/**
 * Property bag backed by a hash map (`PropertyBag` in GenHTTP).
 */
public class MapPropertyBag : PropertyBag {

    private val data = HashMap<String, Any?>()

    override fun get(key: String): Any =
        data[key] ?: throw NoSuchElementException("Key '$key' does not exist in request properties")

    override fun set(key: String, value: Any) {
        data[key] = value
    }

    override fun <T : Any> tryGet(key: String, type: KClass<T>): T? {
        val value = data[key] ?: return null

        @Suppress("UNCHECKED_CAST")
        return if (type.isInstance(value) || type.javaObjectType.isInstance(value) || type.isSuperclassOf(value::class)) value as T else null
    }

    override fun clear(key: String) {
        data[key] = null
    }

    /**
     * Removes all entries.
     */
    public fun clear() {
        data.clear()
    }

    /**
     * Copies all entries to the given map.
     */
    public fun cloneTo(target: MutableMap<String, Any?>) {
        target.putAll(data)
    }

}
