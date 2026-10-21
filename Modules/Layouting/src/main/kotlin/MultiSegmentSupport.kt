@file:JvmName("MultiSegmentSupport")

package org.genhttp.modules.layouting

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.layouting.provider.LayoutBuilder

/**
 * Adds multiple segments (e.g. /api/v1) to the layout and appends the given handler to the last one.
 *
 * @param segments The segments to be added
 * @param handler The handler to be appended
 * @return The builder the segments have been added to
 * @throws IllegalStateException Thrown if there are incompatible handlers already registered at a segment
 */
public fun LayoutBuilder.add(segments: Array<String>, handler: HandlerBuilder): LayoutBuilder {
    if (segments.isEmpty()) {
        return add(handler)
    }

    val last = addSegments(*segments.copyOfRange(0, segments.size - 1))

    last.add(segments[segments.size - 1], handler)

    return this
}

/**
 * Adds all the given segments to the layout and returns the last one.
 *
 * @param segments The segments to be added
 * @return The last created layout
 * @throws IllegalStateException Thrown if there are incompatible handlers already registered at a segment
 */
public fun LayoutBuilder.addSegments(vararg segments: String): LayoutBuilder {
    if (segments.isEmpty()) {
        return this
    }

    var current = this

    for (section in segments) {
        val pathSegment = PathSegment(section)

        val existing = current.routedHandlers[pathSegment]

        if (existing != null) {
            if (existing is LayoutBuilder) {
                current = existing
            } else {
                throw IllegalStateException("Existing section '$section' is no layout")
            }
        } else {
            val newLayout = Layout.create()

            current.add(section, newLayout)
            current = newLayout
        }
    }

    return current
}
