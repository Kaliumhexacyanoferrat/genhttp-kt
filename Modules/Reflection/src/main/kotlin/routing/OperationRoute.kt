package org.genhttp.modules.reflection.routing

/**
 * The route of an operation, consisting of segments.
 *
 * @property name The human-readable representation of the route (e.g. "/users/{id}")
 * @property segments The segments to be matched
 * @property isWildcard Whether the operation handles all requests below the route
 */
public class OperationRoute(
    public val name: String,
    public val segments: List<RoutingSegment>,
    public val isWildcard: Boolean
)
