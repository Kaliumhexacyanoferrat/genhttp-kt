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
 * A copy of a request header that does no longer depend on the request buffer.
 */
public class RetainedRequestHeader internal constructor(source: HttpRequestHeader) : RequestHeader {

    override val path: ByteString = ByteString(copy(source.path.bytes))

    override val protocol: HttpProtocol = HttpProtocol(copy(source.version))

    override val method: RequestMethod = RequestMethod(copy(source.method.bytes))

    override val target: RequestTarget = HttpRequestTarget().apply { apply(path) }

    override val query: RequestQuery = RetainedKeyValueList(source.query)

    override val headers: RequestHeaders = RetainedKeyValueList(source.headers)

    private companion object {

        fun copy(buffer: ByteBuffer): ByteArray {
            val array = ByteArray(buffer.remaining())
            buffer.get(buffer.position(), array)
            return array
        }

    }

}
