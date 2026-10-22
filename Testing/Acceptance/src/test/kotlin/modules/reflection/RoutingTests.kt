package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.engine.shared.types.HttpRequestTarget
import org.genhttp.modules.reflection.routing.OperationRoute
import org.genhttp.modules.reflection.routing.OperationRouter
import org.genhttp.modules.reflection.routing.RoutingSegment
import org.genhttp.modules.reflection.routing.segments.ClosingSegment
import org.genhttp.modules.reflection.routing.segments.RegexSegment
import org.genhttp.modules.reflection.routing.segments.SimpleVariableSegment
import org.genhttp.modules.reflection.routing.segments.StringSegment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoutingTests {

    @Test
    fun testSimpleSegment() {
        val route = createRoute(false, StringSegment("segment"), ClosingSegment(false, false))

        val target = createTarget("/segment")

        val match = OperationRouter.tryMatch(target, route)

        assertNotNull(match)

        assertEquals(0, match.offset)
        assertNull(match.pathArguments)
    }

    @Test
    fun testSimpleSegmentWithTrailingSlash() {
        val route = createRoute(false, StringSegment("segment"), ClosingSegment(false, false))

        val target = createTarget("/segment/")

        val match = OperationRouter.tryMatch(target, route)

        assertNotNull(match)

        assertEquals(1, match.offset)
        assertNull(match.pathArguments)
    }

    @Test
    fun testMissingSlashFailsRouting() {
        val route = createRoute(false, StringSegment("segment"), ClosingSegment(true, false))

        val target = createTarget("/segment")

        val match = OperationRouter.tryMatch(target, route)

        assertNull(match)
    }

    @Test
    fun testNonWildcardDoesNotAllowAdditionalSegments() {
        val route = createRoute(false, StringSegment("segment"), ClosingSegment(false, false))

        val target = createTarget("/segment/another")

        val match = OperationRouter.tryMatch(target, route)

        assertNull(match)
    }

    @Test
    fun testWildcardAllowsAdditionalSegments() {
        val route = createRoute(true, StringSegment("segment"), ClosingSegment(false, true))

        val target = createTarget("/segment/another")

        val match = OperationRouter.tryMatch(target, route)

        assertNotNull(match)

        assertEquals(1, match.offset)
        assertNull(match.pathArguments)
    }

    @Test
    fun testSimpleVariable() {
        val route = createRoute(false, SimpleVariableSegment("var"))

        val target = createTarget("/99/")

        val match = OperationRouter.tryMatch(target, route)

        assertNotNull(match)

        assertEquals(1, match.offset)

        val arguments = assertNotNull(match.pathArguments)
        assertEquals(1, arguments.size)

        matchArgument(arguments, "var", "99")
    }

    @Test
    fun testComplexVariables() {
        val route = createRoute(false, RegexSegment("(?<one>[0-9]{2})-(?<two>[0-9]{2})-:three"))

        val target = createTarget("/99-88-77")

        val match = OperationRouter.tryMatch(target, route)

        assertNotNull(match)

        assertEquals(1, match.offset)

        val arguments = assertNotNull(match.pathArguments)
        assertEquals(3, arguments.size)

        matchArgument(arguments, "one", "99")
        matchArgument(arguments, "two", "88")
        matchArgument(arguments, "three", "77")
    }

    private fun createRoute(wildcard: Boolean, vararg segments: RoutingSegment) = OperationRoute("name", segments.toList(), wildcard)

    private fun createTarget(path: String): RequestTarget {
        val target = HttpRequestTarget()
        target.apply(ByteString(path))

        return target
    }

    private fun matchArgument(arguments: Map<ByteString, ByteString>, key: String, value: String) {
        assertEquals(value, arguments[ByteString(key)].toString())
    }

}
