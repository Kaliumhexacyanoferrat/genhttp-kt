package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.modules.websockets.rawclient.RawWebSocketClient

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The status line and headers of a response read from a socket.
 */
class RawHead(val status: Int, private val headers: Map<String, String>) {

    fun header(name: String): String? = headers[name.lowercase()]

}

/**
 * A response read from a socket.
 */
class RawResponse(val head: RawHead, val body: ByteArray) {

    val status: Int
        get() = head.status

    val text: String
        get() = String(body, Charsets.UTF_8)

}

/**
 * Sends requests and reads responses on plain sockets, so that tests control what is sent when (and what is not
 * read).
 */
object RawClient {

    private val random = SecureRandom()

    /**
     * Connects to the given port on the loopback interface. The buffer sizes are set before connecting, so that they
     * determine the TCP window: a small receive buffer makes a client that does not read push back early.
     */
    fun connect(port: Int, receiveBuffer: Int? = null, sendBuffer: Int? = null): Socket {
        val socket = Socket()

        receiveBuffer?.let { socket.receiveBufferSize = it }
        sendBuffer?.let { socket.sendBufferSize = it }

        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress("127.0.0.1", port), Constants.ClientTimeout)
        socket.soTimeout = Constants.ClientTimeout

        return socket
    }

    /**
     * Connects to the given secure port on the loopback interface and performs the TLS handshake (the certificate
     * of the server is not validated).
     *
     * @param configure Configures the socket before the handshake (e.g. the protocols or cipher suites)
     */
    fun connectSecure(port: Int, receiveBuffer: Int? = null, configure: (SSLSocket) -> Unit = {}): SSLSocket {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(TrustAll), random) }

        val socket = context.socketFactory.createSocket() as SSLSocket

        try {
            receiveBuffer?.let { socket.receiveBufferSize = it }

            socket.connect(InetSocketAddress("127.0.0.1", port), Constants.ClientTimeout)
            socket.soTimeout = Constants.ClientTimeout

            configure(socket)

            socket.startHandshake()
        } catch (e: Exception) {
            socket.close()
            throw e
        }

        return socket
    }

    /**
     * Accepts every certificate.
     */
    object TrustAll : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    }

    /**
     * Creates the bytes of a request without a body.
     */
    fun request(path: String = "/", method: String = "GET", headers: List<Pair<String, String>> = emptyList()): ByteArray = buildString {
        append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
        append("Host: 127.0.0.1\r\n")

        for ((name, value) in headers) {
            append(name).append(": ").append(value).append("\r\n")
        }

        append("\r\n")
    }.toByteArray(Charsets.US_ASCII)

    /**
     * Creates the bytes of a request with the given body.
     */
    fun request(path: String, method: String, body: ByteArray, headers: List<Pair<String, String>> = emptyList()): ByteArray =
        request(path, method, headers + ("Content-Length" to body.size.toString())) + body

    /**
     * Writes the given data and flushes the stream.
     */
    fun send(socket: Socket, data: ByteArray) {
        val output = socket.getOutputStream()

        output.write(data)
        output.flush()
    }

    /**
     * Reads the status line and the headers of a response.
     */
    fun readHead(input: InputStream): RawHead {
        val statusLine = readLine(input)

        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Invalid status line '$statusLine'")

        val headers = mutableMapOf<String, String>()

        while (true) {
            val line = readLine(input)

            if (line.isEmpty()) {
                break
            }

            headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
        }

        return RawHead(status, headers)
    }

    /**
     * Reads a complete response (with a body of known length or chunked).
     */
    fun readResponse(input: InputStream): RawResponse {
        val head = readHead(input)

        val length = head.header("Content-Length")

        val body = when {
            length != null -> readExactly(input, length.toInt())
            head.header("Transfer-Encoding").equals("chunked", ignoreCase = true) -> readChunked(input)
            else -> ByteArray(0)
        }

        return RawResponse(head, body)
    }

    /**
     * Reads the given number of bytes, failing if the stream ends before.
     */
    fun readExactly(input: InputStream, length: Int): ByteArray {
        val data = input.readNBytes(length)

        if (data.size < length) {
            throw IOException("Connection closed after ${data.size} of $length bytes")
        }

        return data
    }

    /**
     * Reads until the end of the stream, returning the number of bytes read and whether the stream ended regularly
     * (false, if the connection has been reset).
     */
    fun drain(input: InputStream): Pair<Long, Boolean> {
        val buffer = ByteArray(64 * 1024)

        var total = 0L

        try {
            while (true) {
                val read = input.read(buffer)

                if (read < 0) {
                    return total to true
                }

                total += read
            }
        } catch (_: IOException) {
            return total to false
        }
    }

    /**
     * Checks that the server closes the given connection within the given time (without sending anything), whether
     * it ends the connection regularly or resets it.
     */
    fun assertClosedByServer(socket: Socket, message: String, timeout: Int = Constants.ClientTimeout) {
        socket.soTimeout = timeout

        val read = try {
            socket.getInputStream().read()
        } catch (_: SocketTimeoutException) {
            fail("$message (still open after $timeout ms)")
        } catch (_: IOException) {
            // reset
            -1
        }

        assertEquals(-1, read, "$message (received data)")
    }

    private fun readChunked(input: InputStream): ByteArray {
        val result = ByteArrayOutputStream()

        while (true) {
            val size = readLine(input).substringBefore(';').trim().toInt(16)

            if (size == 0) {
                // trailers
                while (readLine(input).isNotEmpty()) {
                    // ignored
                }

                return result.toByteArray()
            }

            result.write(readExactly(input, size))
            readLine(input)
        }
    }

    private fun readLine(input: InputStream): String = buildString {
        while (true) {
            val b = input.read()

            if (b < 0) {
                throw IOException("Connection closed while reading a line")
            }

            if (b == '\n'.code) {
                break
            }

            if (b != '\r'.code) {
                append(b.toChar())
            }
        }
    }

    // region Websockets

    /**
     * Opens a websocket connection on the given socket (the server must answer with a 101 response).
     */
    fun upgradeToWebsocket(socket: Socket, path: String = "/") {
        val key = ByteArray(16).also { random.nextBytes(it) }

        val headers = listOf(
            "Upgrade" to "websocket",
            "Connection" to "Upgrade",
            "Sec-WebSocket-Key" to Base64.getEncoder().encodeToString(key),
            "Sec-WebSocket-Version" to "13"
        )

        send(socket, request(path, headers = headers))

        val head = readHead(socket.getInputStream())

        if (head.status != 101) {
            throw IOException("The server did not accept the websocket connection (${head.status})")
        }
    }

    /**
     * Creates a masked text frame sent by a client.
     */
    fun textFrame(text: String): ByteArray = RawWebSocketClient.buildClientFrame(text.toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true)

    /**
     * Reads a text frame sent by the server and returns its payload.
     */
    fun readTextFrame(input: InputStream): String {
        val header = readExactly(input, 2)

        val opcode = header[0].toInt() and 0x0F

        if (opcode != 0x1) {
            throw IOException("Expected a text frame, received opcode $opcode")
        }

        val length = when (val short = header[1].toInt() and 0x7F) {
            126 -> readExactly(input, 2).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            127 -> readExactly(input, 8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }.toInt()
            else -> short
        }

        return String(readExactly(input, length), Charsets.UTF_8)
    }

    // endregion

}

/**
 * Writes chunks to a socket in a background thread, so that a test can observe whether the server stops reading
 * from the connection: once the socket buffers are full, the writes block.
 *
 * @param output The stream of the socket to write to
 * @param maxChunks The number of chunks after which the writer gives up (the server did not push back)
 * @param chunk Creates the chunk with the given index
 */
class PushbackWriter(output: OutputStream, private val maxChunks: Int, chunk: (Int) -> ByteArray) {

    private val written = AtomicInteger()

    private val finished = CountDownLatch(1)

    @Volatile
    private var stopped = false

    @Volatile
    private var failure: Throwable? = null

    init {
        Thread.ofPlatform().daemon().name("pushback-writer").start {
            try {
                while (!stopped && written.get() < maxChunks) {
                    output.write(chunk(written.get()))
                    written.incrementAndGet()
                }

                output.flush()
            } catch (e: Throwable) {
                failure = e
            } finally {
                finished.countDown()
            }
        }
    }

    /**
     * The number of chunks that have been written completely.
     */
    val count: Int
        get() = written.get()

    /**
     * Whether the writer has finished (stopped, given up or failed).
     */
    val isFinished: Boolean
        get() = finished.count == 0L

    /**
     * Waits until the writes did not make progress for the given time (the server does not read anymore) and stops
     * the writer after the chunk it is currently writing. Fails if all chunks could be written or the writes failed.
     *
     * @return The number of chunks written when the writer got stuck
     */
    fun awaitPushback(stall: Duration = 1.seconds, timeout: Duration = 120.seconds): Int {
        val start = TimeSource.Monotonic.markNow()

        var last = -1
        var since = TimeSource.Monotonic.markNow()

        while (start.elapsedNow() < timeout) {
            if (isFinished) {
                failure?.let { throw AssertionError("Failed to write to the server", it) }
                fail("All $maxChunks chunks have been written, the server did not push back")
            }

            val current = written.get()

            if (current != last) {
                last = current
                since = TimeSource.Monotonic.markNow()
            } else if (since.elapsedNow() >= stall) {
                stopped = true
                return current
            }

            Thread.sleep(20)
        }

        fail("The writer did not get stuck within $timeout")
    }

    /**
     * Passes the index of every chunk the server answers to the given function, until the writer finished and all
     * chunks written have been answered. The writer continues with the chunk it got stuck with as soon as the
     * server reads again.
     */
    fun readAnswers(read: (Int) -> Unit) {
        var answered = 0

        while (true) {
            val done = isFinished
            val count = written.get()

            if (answered < count) {
                read(answered++)
            } else if (done) {
                break
            } else {
                // the chunk the writer got stuck with has not been written completely yet
                Thread.sleep(1)
            }
        }

        if (!finished.await(60, TimeUnit.SECONDS)) {
            fail("The writer did not finish")
        }

        failure?.let { throw AssertionError("Failed to write to the server", it) }
    }

}
