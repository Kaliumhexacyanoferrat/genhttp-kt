package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.RequestHeaders
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.RequestQuery
import org.genhttp.api.protocol.RequestTarget

import java.nio.ByteBuffer

/**
 * Header of a request, backed by the memory of the parsed request (`RequestHeader` in GenHTTP).
 */
public class HttpRequestHeader(private val request: HttpRequest) : RequestHeader {

    private val _headers = GlyphKeyValueList(request.source.headers)

    private val _query = GlyphKeyValueList(request.source.queryParameters)

    private val _target = HttpRequestTarget()

    private var _protocol: HttpProtocol = HttpProtocol.Http11

    private var _method: RequestMethod = RequestMethod.Get

    private var _path: ByteString = EmptyPath

    override val method: RequestMethod
        get() = _method

    override val path: ByteString
        get() = _path

    override val target: RequestTarget
        get() = _target

    override val protocol: HttpProtocol
        get() = _protocol

    /**
     * The raw protocol version sent by the client.
     */
    public val version: ByteBuffer
        get() = request.source.version

    override val headers: RequestHeaders
        get() = _headers

    override val query: RequestQuery
        get() = _query

    /**
     * Initializes the header from the currently parsed request.
     */
    public fun apply() {
        val source = request.source

        _path = ByteString(source.path)
        _target.apply(source.path)

        // the well-known values are shared, which saves the allocations and lets comparisons succeed by identity
        _protocol = find(source.version, KnownProtocols) ?: HttpProtocol(source.version)
        _method = find(source.method, KnownMethods) ?: RequestMethod(source.method)
    }

    private class Known<T>(value: String, val view: T) {

        val bytes: ByteArray = value.toByteArray(Charsets.US_ASCII)

    }

    private companion object {

        val EmptyPath = ByteString("")

        val KnownProtocols = arrayOf(
            Known("HTTP/1.1", HttpProtocol.Http11),
            Known("HTTP/1.0", HttpProtocol.Http10)
        )

        val KnownMethods = arrayOf(
            Known("GET", RequestMethod.Get),
            Known("POST", RequestMethod.Post),
            Known("HEAD", RequestMethod.Head),
            Known("PUT", RequestMethod.Put),
            Known("DELETE", RequestMethod.Delete),
            Known("OPTIONS", RequestMethod.Options),
            Known("PATCH", RequestMethod.Patch)
        )

        /**
         * Returns the shared view of the known value with exactly the given content (case-sensitive), if any.
         */
        fun <T> find(value: ByteBuffer, known: Array<Known<T>>): T? {
            val start = value.position()
            val length = value.remaining()

            candidates@ for (candidate in known) {
                val bytes = candidate.bytes

                if (bytes.size != length) {
                    continue
                }

                for (i in 0 until length) {
                    if (value.get(start + i) != bytes[i]) {
                        continue@candidates
                    }
                }

                return candidate.view
            }

            return null
        }

    }

}
