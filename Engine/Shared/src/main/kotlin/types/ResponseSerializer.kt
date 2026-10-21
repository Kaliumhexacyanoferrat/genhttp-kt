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
            writer.writeAscii("Connection: Upgrade\r\n")
        } else if (isHttp10) {
            writer.writeAscii(if (keepAlive) "Connection: Keep-Alive\r\n" else "Connection: Close\r\n")
        } else if (!keepAlive) {
            // HTTP/1.1 connections are persistent by default so we do not need to send a Keep-Alive header
            writer.writeAscii("Connection: Close\r\n")
        }

        val content = response.content

        if (content != null) {
            val type = content.type

            if (type != null) {
                writer.writeAscii("Content-Type: ")
                writer.write(type.bytes)
                writer.writeAscii("\r\n")
            }

            val length = content.length

            if (length != null) {
                writer.writeAscii("Content-Length: ")
                writer.writeNumber(length)
                writer.writeAscii("\r\n")
            } else if (!isUpgrade) {
                writer.writeAscii("Transfer-Encoding: chunked\r\n")
            }

            val encoding = content.encoding

            if (encoding != null) {
                writer.writeAscii("Content-Encoding: ")
                writer.write(encoding.duplicate())
                writer.writeAscii("\r\n")
            }
        } else {
            writer.writeAscii("Content-Length: 0\r\n")
        }

        val headers = response.headers

        for (i in 0 until headers.count) {
            val (key, value) = headers.getMemoryEntry(i)

            writer.write(key.duplicate())
            writer.writeAscii(": ")
            writer.write(value.duplicate())
            writer.writeAscii("\r\n")
        }
    }

}
