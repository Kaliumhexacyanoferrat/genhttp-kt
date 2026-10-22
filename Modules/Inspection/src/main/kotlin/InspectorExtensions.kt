@file:JvmName("InspectorExtensions")

package org.genhttp.modules.inspection

import org.genhttp.api.content.HandlerBuilder

/**
 * Adds an inspection concern to the given builder.
 *
 * @return The builder the concern has been added to
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.addInspector(): T {
    add(Inspector.create())
    return this
}
