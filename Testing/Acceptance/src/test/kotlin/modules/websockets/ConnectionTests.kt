package org.genhttp.testing.acceptance.modules.websockets

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ClientConnection
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.provider.ConnectionSettings
import org.genhttp.modules.websockets.provider.WebsocketConnection
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import org.slf4j.ILoggerFactory

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConnectionTests {

    @Test
    fun testReadFailureAfterFrameIsReported() = test {
        // the frame is followed by a failing read, as with a transport that fails after having delivered data
        val connection = createConnection(FailingInput(maskedText("hello")))

        val frame = connection.readFrame()

        assertEquals(FrameType.Text, frame.type)
        assertEquals("hello", Charsets.UTF_8.decode(frame.data).toString())

        val e = assertFailsWith<IOException> { connection.readFrame() }

        assertEquals("transport failed", e.message)
    }

    @Test
    fun testReadFailureBeforeFirstFrameIsReported() = test {
        val connection = createConnection(FailingInput(null))

        val e = assertFailsWith<IOException> { connection.readFrame() }

        assertEquals("transport failed", e.message)
    }

    private fun createConnection(input: InputStream): WebsocketConnection {
        val server = StubServer()

        val request = StubRequest(server, input)

        val settings = ConnectionSettings(Formatting.default().build(), JsonFormat(), handleContinuationFramesManually = false, allocateFrameData = true)

        return WebsocketConnection(request, StubSink(), settings)
    }

    private fun maskedText(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)

        val mask = byteArrayOf(1, 2, 3, 4)

        val frame = ByteArray(6 + payload.size)

        frame[0] = 0x81.toByte() // FIN, text
        frame[1] = (0x80 or payload.size).toByte() // masked, 7 bit length

        mask.copyInto(frame, 2)

        for (i in payload.indices) {
            frame[6 + i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        }

        return frame
    }

    // region Supporting types

    /**
     * Delivers the given data (if any) and fails with an exception afterward, like a
     * pipe that has been completed with an error.
     */
    private class FailingInput(private var data: ByteArray?) : InputStream() {

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val pending = data

            if (pending != null) {
                data = null

                pending.copyInto(b, off, 0, pending.size)

                return pending.size
            }

            throw IOException("transport failed")
        }

        override fun available(): Int = data?.size ?: 0

    }

    private class StubRequest(override val server: Server, private val input: InputStream) : Request {

        override val endPoint: EndPoint
            get() = throw UnsupportedOperationException()

        override val client: ClientConnection
            get() = throw UnsupportedOperationException()

        override val properties: PropertyBag
            get() = throw UnsupportedOperationException()

        override val header: RequestHeader
            get() = throw UnsupportedOperationException()

        override val hasBody: Boolean
            get() = false

        override fun getBody(headerAccess: HeaderAccess): RequestBody? = throw UnsupportedOperationException()

        override fun wrapBody(wrapper: (RequestBody) -> RequestBody): Unit = throw UnsupportedOperationException()

        override fun respond(): ResponseBuilder = throw UnsupportedOperationException()

        override fun upgrade(): InputStream = input

    }

    private class StubServer : Server {

        override val serverEngine: ServerEngine
            get() = ServerEngine.Internal

        override val version: String
            get() = "stub"

        override val running: Boolean
            get() = true

        override val development: Boolean
            get() = false

        override val handler: Handler
            get() = throw UnsupportedOperationException()

        override val properties: PropertyBag
            get() = throw UnsupportedOperationException()

        override val logging: ILoggerFactory
            get() = throw UnsupportedOperationException()

        override val endPoints: EndPointCollection
            get() = throw UnsupportedOperationException()

        override suspend fun start() {}

        override suspend fun close() {}

    }

    private class StubSink : ResponseSink {

        override val stream: OutputStream = ByteArrayOutputStream()

        override val writer: WritableByteChannel = Channels.newChannel(stream)

    }

    // endregion

}
