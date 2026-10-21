package org.genhttp.modules.reflection.routing.segments

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.modules.reflection.routing.PathArgumentSink
import org.genhttp.modules.reflection.routing.RoutingSegment
import org.genhttp.modules.reflection.toParameter

/**
 * A segment matched by a regular expression, providing named groups as path arguments.
 */
internal class RegexSegment(definition: String) : RoutingSegment {

    private val matcher: Regex

    override val providedArguments: Array<String>

    init {
        val arguments = mutableListOf<String>()

        var pattern = definition

        // convert parameters of the format ":var" into appropriate groups
        for (match in VarPattern.findAll(definition)) {
            val name = match.groupValues[1]
            pattern = pattern.replace(match.value, name.toParameter())
            arguments.add(name)
        }

        // convert advanced regex params as well
        for (match in RegexPattern.findAll(definition)) {
            arguments.add(match.groupValues[1])
        }

        matcher = Regex("^$pattern$")
        providedArguments = arguments.toTypedArray()
    }

    override fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int> {
        val part = target.next(offset) ?: return false to 0

        val result = matcher.matchEntire(part.toString()) ?: return false to 0

        for (name in providedArguments) {
            val group = result.groups[name] ?: continue
            argumentSink.add(ByteString(name), ByteString(group.value))
        }

        return true to 1
    }

    private companion object {

        val VarPattern = Regex("\\:([a-z]+)", RegexOption.IGNORE_CASE)

        val RegexPattern = Regex("\\(\\?\\<([a-z]+)\\>([^)]+)\\)", RegexOption.IGNORE_CASE)

    }

}
