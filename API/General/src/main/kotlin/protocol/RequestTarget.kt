package org.genhttp.api.protocol

import java.nio.ByteBuffer

/**
 * The target resource requested by the client. Enables routing by storing the processing state across handlers.
 */
public interface RequestTarget {

    /**
     * The current segment that needs to be routed by the middleware.
     *
     * Acts as a pointer on the routing target that can be moved forward via [advance]. Handlers that are
     * concerned with routing will analyze the current value and decide their action based on it. Afterward,
     * they will advance the target, so child handlers will not see the path prefix.
     */
    public val current: PathSegment?

    /**
     * Specifies whether this is the last segment to be handled.
     */
    public val isLast: Boolean

    /**
     * Specifies, whether the request path ends with a trailing slash ('/').
     */
    public val hasTrailingSlash: Boolean

    /**
     * Advances the routing pointer by the given number of segments.
     *
     * @param segments The number of segments to advance by
     */
    public fun advance(segments: Int = 1)

    /**
     * Peeks at the next segment with the given offset (zero-based).
     *
     * @param offset The offset of the segment to read
     * @return The path segment at the specified offset
     */
    public fun next(offset: Int): PathSegment?

    /**
     * Creates a new routing target with the given suffix appended. For example, you can use this method to
     * add a file extension to an existing path (such as appending '.gz' to 'file.txt').
     *
     * The resulting request target will have the same routing state as this instance.
     *
     * @param suffix The suffix to be appended to the target
     * @return The newly created copy
     */
    public fun copyAndAppend(suffix: ByteBuffer): RequestTarget

    /**
     * Creates a string representation of the request target.
     *
     * @param decode Whether URL encoded characters should be decoded
     * @param remainingOnly Whether the whole path or only the non-routed parts should be included
     * @return The requested string representation
     */
    public fun asString(decode: Boolean = true, remainingOnly: Boolean = false): String

}
