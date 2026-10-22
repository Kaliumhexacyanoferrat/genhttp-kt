@file:JvmName("KeyValueListExtensions")

package org.genhttp.api.protocol

/**
 * Tries to fetch the entry with the given key from the list.
 *
 * @param key The key of the item to be fetched (compared ASCII case-insensitively)
 * @return The value of the first matching entry or null, if the entry could not be found
 */
public fun KeyValueList.getEntry(key: ByteString): ByteString? {
    val length = key.length

    for (i in 0 until count) {
        val (entryKey, entryValue) = getMemoryEntry(i)

        if (entryKey.remaining() == length && key.contentEquals(entryKey)) {
            return ByteString(entryValue)
        }
    }

    return null
}

/**
 * Tries to fetch the entry with the given key from the list.
 *
 * @param key The key of the item to be fetched (compared ASCII case-insensitively)
 * @return The value of the first matching entry or null, if the entry could not be found
 */
public fun KeyValueList.getEntry(key: String): String? = getEntry(ByteString(key))?.toString()

/**
 * Checks, whether the list contains the given key.
 *
 * @param key The key to search for
 * @return true, if the list contains an entry with that key, otherwise false
 */
public fun KeyValueList.containsKey(key: ByteString): Boolean {
    for (i in 0 until count) {
        if (key.contentEquals(getMemoryEntry(i).first)) {
            return true
        }
    }

    return false
}

/**
 * Checks, whether the list contains the given key.
 *
 * @param key The key to search for
 * @return true, if the list contains an entry with that key, otherwise false
 */
public fun KeyValueList.containsKey(key: String): Boolean = containsKey(ByteString(key))

/**
 * Fetches an entry of the list by index and converts it into byte strings.
 *
 * @param index The zero based index of the item to be fetched
 * @return The entry as byte strings
 */
public fun KeyValueList.getStringEntry(index: Int): Pair<ByteString, ByteString> {
    val (key, value) = getMemoryEntry(index)
    return ByteString(key) to ByteString(value)
}
