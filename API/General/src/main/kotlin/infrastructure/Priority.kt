package org.genhttp.api.infrastructure

/**
 * Defines the priority of an item in a list.
 *
 * @property value The numeric value of the priority (higher values take precedence)
 */
public enum class Priority(public val value: Int) {

    /** Low priority. */
    Low(0),

    /** Medium priority. */
    Medium(50),

    /** High priority. */
    High(100)

}
