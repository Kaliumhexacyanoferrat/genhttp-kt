package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.containsKey
import org.genhttp.engine.shared.io.ConnectionWriter

/**
 * Serializes the header of a HTTP/1.x response.
 */
public object ResponseSerializer {

    /**
     * Checks whether the body of the given response should be sent to the client.
     */
    public fun shouldSendBody(request: Request?, response: Response, headRequest: Boolean): Boolean {
        if (request == null) {
            return true
        }

        if (headRequest) {
            return false
        }

        val content = response.content ?: return false

        return (content.length ?: 1) > 0
    }

    /**
     * Writes the headers of the given response (without status line and without the terminating empty line).
     */
    public fun writeHeader(writer: ConnectionWriter, response: Response, keepAlive: Boolean, serverHeader: ByteArray, dateHeader: ByteArray, isHttp10: Boolean) {
        val isUpgrade = response.mode == Connection.Upgrade

        if (!response.headers.containsKey(KnownHeaders.Server)) {
            writer.write(serverHeader)
        }

        if (!response.headers.containsKey(KnownHeaders.Date)) {
            writer.write(dateHeader)
        }

        if (isUpgrade) {
            writer.write(ConnectionUpgrade)
        } else if (isHttp10) {
            writer.write(if (keepAlive) ConnectionKeepAlive else ConnectionClose)
        } else if (!keepAlive) {
            // HTTP/1.1 connections are persistent by default so we do not need to send a Keep-Alive header
            writer.write(ConnectionClose)
        }

        val content = response.content

        if (content != null) {
            val type = content.type

            if (type != null) {
                writer.write(ContentTypeName)
                writer.write(type.bytes)
                writer.write(LineEnd)
            }

            val length = content.length

            if (length != null) {
                writer.write(ContentLengthName)
                writer.writeNumber(length)
                writer.write(LineEnd)
            } else if (!isUpgrade) {
                writer.write(TransferEncodingChunked)
            }

            val encoding = content.encoding

            if (encoding != null) {
                writer.write(ContentEncodingName)
                writer.write(encoding.duplicate())
                writer.write(LineEnd)
            }
        } else {
            writer.write(ContentLengthZero)
        }

        val headers = response.headers

        for (i in 0 until headers.count) {
            val (key, value) = headers.getMemoryEntry(i)

            writer.write(key.duplicate())
            writer.write(Separator)
            writer.write(value.duplicate())
            writer.write(LineEnd)
        }
    }

    private val ConnectionUpgrade = ascii("Connection: Upgrade\r\n")

    private val ConnectionKeepAlive = ascii("Connection: Keep-Alive\r\n")

    private val ConnectionClose = ascii("Connection: Close\r\n")

    private val ContentTypeName = ascii("Content-Type: ")

    private val ContentLengthName = ascii("Content-Length: ")

    private val ContentLengthZero = ascii("Content-Length: 0\r\n")

    private val ContentEncodingName = ascii("Content-Encoding: ")

    private val TransferEncodingChunked = ascii("Transfer-Encoding: chunked\r\n")

    private val Separator = ascii(": ")

    private val LineEnd = ascii("\r\n")

    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

}
