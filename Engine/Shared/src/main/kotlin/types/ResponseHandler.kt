package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.engine.shared.types.sinks.ChunkedSink
import org.genhttp.engine.shared.types.sinks.RegularSink

/**
 * Writes responses to the connection of a client.
 */
public class ResponseHandler(private val context: ClientContext) {

    private val regularSink = RegularSink(context)

    private val chunkedSink = ChunkedSink(context)

    /**
     * Writes the given response to the client.
     *
     * @return true, if the response has been written successfully
     */
    public suspend fun handle(request: Request?, response: Response, version: HttpProtocol, keepAlive: Boolean, headRequest: Boolean): Boolean {
        try {
            val writer = context.writer

            writer.write(StatusLine.get(response.status))

            ResponseSerializer.writeHeader(writer, response, keepAlive, ServerHeader.getValue(context), DateHeader.getValue(), version == HttpProtocol.Http10)

            writer.writeAscii("\r\n")

            if (ResponseSerializer.shouldSendBody(request, response, headRequest)) {
                writeBody(response)
            }

            return true
        } catch (e: Exception) {
            if (!ConnectionExceptions.isGracefulDisconnect(e)) {
                context.server.logging.getLogger(ResponseHandler::class.java.name).warn("Failed to write response to client", e)
            }

            return false
        }
    }

    private suspend fun writeBody(response: Response) {
        val content = response.content ?: return

        try {
            if (content.length == null && response.mode != Connection.Upgrade) {
                writeChunked(content)
            } else {
                content.write(regularSink)
            }
        } finally {
            (content as? AutoCloseable)?.close()
        }
    }

    private suspend fun writeChunked(content: ResponseContent) {
        content.write(chunkedSink)
        chunkedSink.finish()
    }

}
