package org.genhttp.api.infrastructure

/**
 * The protocols supported by a port binding.
 *
 * @property flags The bit flags of the protocols contained in this value (HTTP/1 = 1, HTTP/2 = 2, HTTP/3 = 4)
 */
public enum class HttpProtocols(public val flags: Int) {

    /** No protocol. */
    None(0),

    /** HTTP/1.1 */
    Http1(1),

    /** HTTP/2 */
    Http2(2),

    /** HTTP/3 */
    Http3(4),

    /** HTTP/1.1 and HTTP/2 */
    Http1AndHttp2(1 or 2),

    /** HTTP/1.1 and HTTP/3 */
    Http1AndHttp3(1 or 4),

    /** HTTP/2 and HTTP/3 */
    Http2AndHttp3(2 or 4),

    /** All supported protocols. */
    All(1 or 2 or 4);

    /**
     * Checks whether this value contains all protocols of the given value (`HasFlag` in .NET).
     */
    public fun hasFlag(other: HttpProtocols): Boolean = (flags and other.flags) == other.flags

    /**
     * Returns the value that contains the protocols of this value without the given ones.
     */
    public operator fun minus(other: HttpProtocols): HttpProtocols = fromFlags(flags and other.flags.inv())

    /**
     * Returns the value that contains the protocols of both values.
     */
    public operator fun plus(other: HttpProtocols): HttpProtocols = fromFlags(flags or other.flags)

    /**
     * Lookup of protocol combinations.
     */
    public companion object {

        /**
         * Returns the value representing the given bit flags.
         */
        public fun fromFlags(flags: Int): HttpProtocols =
            entries.firstOrNull { it.flags == flags } ?: throw IllegalArgumentException("Invalid protocol flags: $flags")

    }

}
