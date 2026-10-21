package org.genhttp.modules.layouting.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.add

/**
 * Builds a handler that routes requests to other handlers by the segments of their path.
 */
public class LayoutBuilder : HandlerBuilder.WithConcerns<LayoutBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var index: HandlerBuilder? = null

    // region Get-/Setters

    internal val routedHandlers: MutableMap<PathSegment, HandlerBuilder> = LinkedHashMap()

    internal val rootHandlers: MutableList<HandlerBuilder> = mutableListOf()

    // endregion

    // region Functionality

    /**
     * Sets the handler which should be invoked to provide the index of the layout.
     *
     * @param handler The handler used for the index of the layout
     */
    public fun index(handler: Handler): LayoutBuilder = index(HandlerWrapper(handler))

    /**
     * Sets the handler which should be invoked to provide the index of the layout.
     *
     * @param handler The handler used for the index of the layout
     */
    public fun index(handler: HandlerBuilder): LayoutBuilder {
        index = handler
        return this
    }

    /**
     * Adds a handler that will be invoked for all URLs below the specified path segment.
     *
     * @param name The name of the path segment to be handled
     * @param handler The handler which will handle the segment
     */
    public fun add(name: String, handler: Handler): LayoutBuilder = add(name, HandlerWrapper(handler))

    /**
     * Adds a handler that will be invoked for all URLs below the specified path segment.
     *
     * @param name The name of the path segment to be handled
     * @param handler The handler which will handle the segment
     * @throws IllegalStateException Thrown if a segment with the given name has already been added
     */
    public fun add(name: String, handler: HandlerBuilder): LayoutBuilder {
        if (name.contains('/')) {
            return this.add(name.split('/').filter { it.isNotEmpty() }.toTypedArray(), handler)
        }

        val segment = PathSegment(name)

        if (routedHandlers.containsKey(segment)) {
            throw IllegalStateException("A segment with the name '$name' has already been added to the layout")
        }

        routedHandlers[segment] = handler

        return this
    }

    /**
     * Adds a handler on root level that will be invoked if neither a path segment has been detected
     * nor the index has been invoked.
     *
     * Can be used to provide one or multiple fallback handlers for the layout. Fallback handlers will
     * be executed in the order they have been added to the layout.
     *
     * @param handler The root level handler to be added
     */
    public fun add(handler: Handler): LayoutBuilder = add(HandlerWrapper(handler))

    /**
     * Adds a handler on root level that will be invoked if neither a path segment has been detected
     * nor the index has been invoked.
     *
     * Can be used to provide one or multiple fallback handlers for the layout. Fallback handlers will
     * be executed in the order they have been added to the layout.
     *
     * @param handler The root level handler to be added
     */
    public fun add(handler: HandlerBuilder): LayoutBuilder {
        rootHandlers.add(handler)
        return this
    }

    override fun add(concern: ConcernBuilder): LayoutBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Creates a new layout and registers it at the given path.
     *
     * @param segment The path of the segment to be added
     * @return The newly created segment
     */
    public fun addSegment(segment: String): LayoutBuilder {
        val child = Layout.create()

        add(segment, child)

        return child
    }

    override fun build(): Handler {
        val routed = LinkedHashMap<PathSegment, Handler>()

        for ((key, value) in routedHandlers) {
            routed[key] = value.build()
        }

        val root = rootHandlers.map { it.build() }

        return Concerns.chain(concerns, LayoutHandler(routed, root, index?.build()))
    }

    // endregion

}
