package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.io.WriteTarget
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.HttpRequestBody
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.flexibleparser.FlexibleParser
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser
import org.genhttp.testing.acceptance.engine.body.ContentLengthFramingTests.Companion.ascii
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.TestPipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

import org.junit.jupiter.api.Test

import org.slf4j.ILoggerFactory

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestBodyLifecycleTests {

    // region Finishing

    @Test
    fun testFinishWithoutBody() = test {
        val (request, pipe) = parse("GET / HTTP/1.1\r\nHost: localhost\r\n\r\nNEXT")

        assertFalse(request.hasBody)

        assertTrue(request.finish())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testFinishSkipsUnreadContentLength() = test {
        val (request, pipe) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHelloNEXT")

        assertTrue(request.hasBody)

        assertTrue(request.finish())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testFinishSkipsUnreadChunked() = test {
        val (request, pipe) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHello\r\n0\r\n\r\nNEXT")

        assertTrue(request.finish())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testFinishSkipsPartiallyReadBody() = test {
        val (request, pipe) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 10\r\n\r\nHelloWorldNEXT")

        val body = request.getBody()

        assertNotNull(body)

        body.asStream().readNBytes(ByteArray(3), 0, 3)

        assertTrue(request.finish())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testFinishReleasesMemoryView() = test {
        val (request, pipe) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHelloNEXT")

        val memory = request.getBody()!!.asMemory()

        assertEquals("Hello", ascii(memory))

        assertTrue(request.finish())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testFinishWithTruncatedBody() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 10\r\n\r\nHello")

        assertFalse(request.finish())
    }

    @Test
    fun testFinishWithInvalidLength() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: abc\r\n\r\nHello", validate = false)

        assertFalse(request.finish())
    }

    @Test
    fun testFinishWithUnsupportedEncoding() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip\r\n\r\nHello", validate = false)

        assertFalse(request.finish())
    }

    @Test
    fun testFinishAfterUpgrade() = test {
        val (request, _) = parse("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")

        request.upgrade()

        assertFalse(request.finish())
    }

    // endregion

    // region Body access

    @Test
    fun testNoBody() = test {
        val (request, _) = parse("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")

        assertNull(request.getBody())
    }

    @Test
    fun testBodyCanOnlyBeFetchedOnce() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        request.getBody()

        assertFailsWith<IllegalStateException> { request.getBody() }
    }

    @Test
    fun testHeaderIsRetained() = test {
        val (request, _) = parse("POST /path HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        request.getBody()

        assertEquals("/path", request.header.target.asString(decode = false))
    }

    @Test
    fun testHeaderIsReleased() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        request.getBody(HeaderAccess.Release)

        assertFailsWith<IllegalStateException> { request.header }
    }

    @Test
    fun testInvalidLength() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: abc\r\n\r\nHello", validate = false)

        val e = assertFailsWith<ProviderException> { request.getBody() }

        assertEquals(ResponseStatus.BadRequest, e.status)
    }

    @Test
    fun testUnsupportedEncoding() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip\r\n\r\nHello", validate = false)

        val e = assertFailsWith<ProviderException> { request.getBody() }

        assertEquals(ResponseStatus.NotImplemented, e.status)
    }

    @Test
    fun testDeclaredLength() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        val body = request.getBody() as HttpRequestBody

        assertEquals(5L, body.length)
    }

    @Test
    fun testMemoryContinuesStream() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        val body = request.getBody()!!

        val stream = body.asStream()

        assertSame(stream, body.asStream())

        assertEquals(2, stream.read(ByteArray(2)))

        assertEquals("llo", ascii(body.asMemory()))
    }

    @Test
    fun testStreamContinuesMemory() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        val body = request.getBody()!!

        val first = body.asMemory()
        val second = body.asMemory()

        assertTrue(first == second)

        // the end of the stream is signalled by -1 on the JVM (0 upstream)
        assertEquals(-1, body.asStream().read(ByteArray(16)))
    }

    @Test
    fun testMemoryOfDelayedBody() = test {
        val (request, pipe) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\n", complete = false)

        val body = request.getBody()!!

        val pending = async(Dispatchers.IO) { body.asMemory() }

        assertFalse(pending.isCompleted)

        pipe.write("Hello")

        assertEquals("Hello", ascii(pending.await()))
        assertEquals("Hello", ascii(body.asMemory()))
    }

    @Test
    fun testWrappedBodyIsDisposed() = test {
        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")

        request.wrapBody { inner -> DisposableBody(inner) }

        val body = request.getBody()

        val wrapped = assertIs<DisposableBody>(body)

        request.reset()

        assertTrue(wrapped.disposed)
    }

    // endregion

    // region Upgrades

    @Test
    fun testUpgrade() = test {
        val (request, pipe) = parse("GET /socket HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\n\r\nNEXT")

        val reader = request.upgrade()

        // the upgraded connection is provided as a stream on the reader of the connection (upstream returns the
        // reader itself), so the stream exposes the data buffered by the reader of the pipe
        assertEquals(pipe.reader.available, reader.available())

        assertFalse(request.hasBody)
        assertEquals("/socket", request.header.target.asString(decode = false))

        assertFailsWith<IllegalStateException> { request.getBody() }

        // reads the rest of the pipe through the upgraded stream (the reader of the pipe upstream)
        assertEquals("NEXT", String(reader.readAllBytes(), Charsets.US_ASCII))
    }

    @Test
    fun testUpgradeAfterReleasingHeader() = test {
        val (request, _) = parse("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")

        request.getBody(HeaderAccess.Release)

        request.upgrade()

        assertFailsWith<IllegalStateException> { request.header }
    }

    // endregion

    // region Expect: 100-continue

    @Test
    fun testContinueSentWithFirstRead() = test {
        val output = TestOutput()

        val (request, _) = parse(ExpectingRequest, output = output.writer)

        val body = request.getBody()!!

        assertEquals("", written(output))

        val memory = body.asMemory()

        assertEquals("Hello", ascii(memory))
        assertEquals(Continue, written(output))
    }

    @Test
    fun testContinueSentOnce() = test {
        val output = TestOutput()

        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nExpect: 100-continue\r\n\r\n2\r\nHe\r\n3\r\nllo\r\n0\r\n\r\n", output = output.writer)

        val stream = request.getBody()!!.asStream()

        val buffer = ByteArray(2)

        // the end of the stream is signalled by -1 on the JVM (0 upstream)
        while (stream.read(buffer) > 0) { }

        assertEquals(Continue, written(output))
    }

    @Test
    fun testContinueIgnoresCase() = test {
        val output = TestOutput()

        val (request, _) = parse(ExpectingRequest.replace("100-continue", "100-Continue"), output = output.writer)

        request.getBody()!!.asMemory()

        assertEquals(Continue, written(output))
    }

    @Test
    fun testNoContinueAfterResponseStarted() = test {
        val output = TestOutput()

        val (request, _) = parse(ExpectingRequest, output = output.writer)

        val body = request.getBody()!!

        request.responseStarting()

        val memory = body.asMemory()

        assertEquals("Hello", ascii(memory))
        assertEquals("", written(output))
    }

    @Test
    fun testNoContinueWhenSkipped() = test {
        val output = TestOutput()

        val (request, pipe) = parse(ExpectingRequest + "NEXT", output = output.writer)

        request.responseStarting()

        // the final response, which has not been flushed by the engine yet
        output.writer.write("HTTP/1.1 200 OK\r\n\r\n".toByteArray(Charsets.US_ASCII))

        assertTrue(request.finish())

        assertEquals("HTTP/1.1 200 OK\r\n\r\n", written(output))
        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testNoContinueWhenSkippedAfterContinue() = test {
        val output = TestOutput()

        val (request, pipe) = parse(ExpectingRequest + "NEXT", output = output.writer)

        val stream = request.getBody()!!.asStream()

        assertEquals(2, stream.read(ByteArray(2)))

        assertTrue(request.finish())

        assertEquals(Continue, written(output))
        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testNoContinueForHttp10() = test {
        val output = TestOutput()

        val (request, _) = parse(ExpectingRequest.replace("HTTP/1.1", "HTTP/1.0"), output = output.writer)

        request.getBody()!!.asMemory()

        assertEquals("", written(output))
    }

    @Test
    fun testNoContinueWithoutBody() = test {
        val output = TestOutput()

        val (request, _) = parse("POST / HTTP/1.1\r\nHost: localhost\r\nExpect: 100-continue\r\n\r\n", output = output.writer)

        assertNull(request.getBody())
        assertTrue(request.finish())

        assertEquals("", written(output))
    }

    @Test
    fun testNoContinueWithoutWriter() = test {
        val (request, _) = parse(ExpectingRequest)

        val memory = request.getBody()!!.asMemory()

        assertEquals("Hello", ascii(memory))
    }

    @Test
    fun testContinueNotCarriedOver() = test {
        val output = TestOutput()

        val (request, _) = parse(ExpectingRequest, output = output.writer)

        request.responseStarting()

        assertTrue(request.finish())

        request.reset()

        written(output)

        val pipe = TestPipe()

        pipe.write("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello")
        pipe.complete()

        // the engines consume the header before the request is applied
        pipe.reader.fill()

        val bytesRead = UltraHardenedParser.tryExtractFullHeader(pipe.reader.peek(), request.source, ParserLimits.Default)

        assertTrue(bytesRead >= 0)

        pipe.reader.consume(bytesRead + 1)

        request.apply(TestServer, TestEndPoint, pipe.reader, null, null, output.writer)

        request.getBody()!!.asMemory()

        assertEquals("", written(output))
    }

    /**
     * Returns what has been flushed to the given output so far.
     */
    private fun written(output: TestOutput): String = output.take()

    // endregion

    // region Without connection

    @Test
    fun testWithoutConnection() = test {
        val request = HttpRequest()

        request.apply(TestServer)

        assertFalse(request.hasBody)
        assertNull(request.getBody())

        assertTrue(request.finish())

        assertFailsWith<IllegalStateException> { request.upgrade() }
    }

    // endregion

    // region Helpers

    private fun parse(raw: String, complete: Boolean = true, validate: Boolean = true, output: ConnectionWriter? = null): Pair<HttpRequest, TestPipe> {
        val pipe = TestPipe()

        pipe.write(raw)

        if (complete) {
            pipe.complete()
        }

        val request = HttpRequest()

        // the engines consume the header before the request is applied
        pipe.reader.fill()

        val bytesRead = if (validate) {
            UltraHardenedParser.tryExtractFullHeader(pipe.reader.peek(), request.source, ParserLimits.Default)
        } else {
            // picohttpparser (not ported) does not validate the framing headers, neither does the flexible parser of
            // glyph11, so they reach the request as sent
            FlexibleParser.tryExtractFullHeader(pipe.reader.peek(), request.source)
        }

        assertTrue(bytesRead >= 0)

        pipe.reader.consume(bytesRead + 1)

        request.apply(TestServer, TestEndPoint, pipe.reader, null, null, output)

        return Pair(request, pipe)
    }

    private class DisposableBody(private val inner: RequestBody) : RequestBody, AutoCloseable {

        var disposed: Boolean = false
            private set

        override fun asStream(): InputStream = inner.asStream()

        override suspend fun asMemory(): ByteBuffer = inner.asMemory()

        override fun close() {
            disposed = true
        }

    }

    /**
     * Counterpart of the `Pipe` the upstream tests pass the writer of to the request: the [writer] passes its data
     * to this target only when it sends it to the connection (on flush or for data exceeding its buffer), so the
     * output collects what has been flushed so far.
     */
    private class TestOutput : WriteTarget {

        private val flushed = ByteArrayOutputStream()

        val writer: ConnectionWriter = ConnectionWriter(this)

        override val isOpen: Boolean
            get() = true

        override fun rent(capacity: Int): ByteBuffer = ByteBuffer.allocate(capacity)

        override fun send(buffer: ByteBuffer) {
            write(buffer)
        }

        override fun release(buffer: ByteBuffer) {
            // heap buffers are not pooled
        }

        @Synchronized
        override fun write(data: ByteBuffer) {
            while (data.hasRemaining()) {
                flushed.write(data.get().toInt())
            }
        }

        /**
         * Returns the (ASCII) text flushed so far and removes it from the output.
         */
        @Synchronized
        fun take(): String {
            val result = flushed.toString(Charsets.US_ASCII)

            flushed.reset()

            return result
        }

    }

    /**
     * Counterpart of the NSubstitute stub `Substitute.For<IServer>()`.
     */
    private object TestServer : Server {

        override val serverEngine: ServerEngine get() = notNeeded()

        override val version: String get() = notNeeded()

        override val running: Boolean get() = notNeeded()

        override val development: Boolean get() = notNeeded()

        override val properties: PropertyBag get() = notNeeded()

        override val logging: ILoggerFactory get() = notNeeded()

        override val endPoints: EndPointCollection get() = notNeeded()

        override val handler: Handler get() = notNeeded()

        override suspend fun start() = notNeeded()

        override suspend fun close() = notNeeded()

    }

    /**
     * Counterpart of the NSubstitute stub `Substitute.For<IEndPoint>()` (members return their default values).
     */
    private object TestEndPoint : EndPoint {

        override val address: InetAddress? get() = null

        override val port: Int get() = 0

        override val protocols: HttpProtocols get() = notNeeded()

        override val dualStack: Boolean get() = false

        override val secure: Boolean get() = false

        override fun close() {
            // nothing to release
        }

    }

    // endregion

    private companion object {

        const val Continue = "HTTP/1.1 100 Continue\r\n\r\n"

        const val ExpectingRequest = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\nHello"

        fun notNeeded(): Nothing = throw UnsupportedOperationException("Not needed by the test")

    }

}
