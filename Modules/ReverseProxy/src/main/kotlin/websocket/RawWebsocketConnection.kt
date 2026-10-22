package org.genhttp.modules.reverseproxy.websocket

import kotlinx.coroutines.runInterruptible

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.Locale

import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A small helper around a raw TCP socket that knows how to connect to a ws:// or wss:// URL, wrap it in the
 * right stream, and perform the HTTP upgrade handshake.
 *
 * The constructor parses the URL into host, port, whether the port is default, whether the connection is
 * secure (wss/https is encrypted via TLS) and the route (path with query). [initializeStream] connects the socket,
 * optionally upgrades it to a TLS socket for secure endpoints, and then creates a [DuplexPipe] over the streams
 * with configurable RX/TX buffer sizes. [tryUpgrade] builds a WebSocket upgrade GET request by hand (including a
 * correct Host header that omits the port when it is the default), writes it to the pipe, and reads from the
 * input until it finds the HTTP header terminator. The bytes following the terminator are retained, so they can
 * be read from the pipe afterward. [close] cleans up the pipe, the underlying streams and the socket.
 *
 * @param url The URL of the server to connect to
 * @param rxMaxBufferSize The size of the buffer used to read from the server
 * @param txMaxBufferSize The size of the buffer used to write to the server
 */
public class RawWebsocketConnection(
    url: String,
    private val rxMaxBufferSize: Int = 4096 * 4,
    private val txMaxBufferSize: Int = 4096
) : AutoCloseable {

    private val secure: Boolean

    private val host: String
    private val port: Int
    private val isDefaultPort: Boolean
    private val route: ByteArray

    private var socket: Socket = Socket().apply { tcpNoDelay = true }

    /**
     * The pipe to communicate with the server, available after [initializeStream] has been called.
     */
    public var pipe: DuplexPipe? = null
        private set

    init {
        val (host, port, isDefaultPort, secure, route) = parse(url)

        this.host = host
        this.port = port
        this.isDefaultPort = isDefaultPort
        this.secure = secure
        this.route = route
    }

    // region Functionality

    /**
     * Connects to the server and prepares the streams used to communicate with it.
     */
    public suspend fun initializeStream() {
        try {
            runInterruptible {
                // the socket is created for IPv4 upstream
                val addresses = InetAddress.getAllByName(host.removePrefix("[").removeSuffix("]"))

                val ipv4 = addresses.filterIsInstance<Inet4Address>()

                val candidates = ipv4.ifEmpty { addresses.toList() }

                var failure: IOException? = null

                var connected = false

                for (address in candidates) {
                    try {
                        socket.connect(InetSocketAddress(address, port))
                        connected = true
                        break
                    } catch (e: IOException) {
                        failure = e

                        socket.close()
                        socket = Socket().apply { tcpNoDelay = true }
                    }
                }

                if (!connected) {
                    throw failure ?: IOException("Unable to connect to $host:$port")
                }

                if (secure) {
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory

                    val ssl = factory.createSocket(socket, host, port, true) as SSLSocket

                    ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }

                    socket = ssl

                    ssl.startHandshake()
                }

                pipe = DuplexPipe(
                    BufferedInputStream(socket.getInputStream(), rxMaxBufferSize),
                    BufferedOutputStream(socket.getOutputStream(), txMaxBufferSize)
                )
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /**
     * Sends the upgrade request to the server and waits for the response headers.
     *
     * Cancel the invoking coroutine to abort waiting for the response.
     *
     * @param request The request received from the client, whose headers will be relayed to the server
     */
    public suspend fun tryUpgrade(request: Request) {
        val pipe = pipe ?: throw IllegalStateException("Not initialized.")

        val buffer = ByteArrayOutputStream()

        buffer.write("GET ".toByteArray(Charsets.US_ASCII))
        buffer.write(route)
        buffer.write(" HTTP/1.1".toByteArray(Charsets.US_ASCII))
        buffer.write(CrLf)

        buffer.write("Host: ".toByteArray(Charsets.US_ASCII))
        buffer.write(host.toByteArray(Charsets.US_ASCII))

        if (!isDefaultPort) {
            buffer.write(":".toByteArray(Charsets.US_ASCII))
            buffer.write(port.toString().toByteArray(Charsets.US_ASCII))
        }

        buffer.write(CrLf)

        val headers = request.header.headers

        for (i in 0 until headers.count) {
            val (key, value) = headers.getMemoryEntry(i)

            if (KnownHeaders.Host.contentEquals(key)) {
                continue
            }

            buffer.write(toArray(key))
            buffer.write(":".toByteArray(Charsets.US_ASCII))
            buffer.write(toArray(value))
            buffer.write(CrLf)
        }

        buffer.write(CrLf)

        // Writes and flushes
        runInterruptible {
            pipe.output.write(buffer.toByteArray())
            pipe.output.flush()
        }

        // Read the handshake response until \r\n\r\n
        while (request.server.running) {
            if (runInterruptible { readHandshake(pipe.input) }) {
                return
            }
        }
    }

    // Consumes the response headers, but not the bytes following them. Returns true if the whole header has been read.
    private fun readHandshake(input: InputStream): Boolean {
        var matched = 0

        while (true) {
            val read = try {
                input.read()
            } catch (e: IOException) {
                // A peer that closes the socket while our request bytes are still unread in its
                // receive buffer triggers an RST (RFC 1122, 4.2.2.13) instead of a clean FIN, so
                // the read surfaces as ECONNRESET rather than an EOF. Both cases mean the same
                // thing here: the upstream went away mid-handshake.
                throw ProviderException(ResponseStatus.BadGateway, "Connection was reset before the full WebSocket handshake was received.", e)
            }

            if (read < 0) {
                throw ProviderException(ResponseStatus.BadGateway, "Connection closed before full WebSocket handshake was received.")
            }

            if (read == Terminator[matched].toInt()) {
                matched++

                if (matched == Terminator.size) {
                    return true
                }
            } else {
                matched = if (read == Terminator[0].toInt()) 1 else 0
            }
        }
    }

    /**
     * Closes the pipe, the streams and the socket.
     */
    override fun close() {
        // closing the socket first unblocks pending reads
        try {
            socket.close()
        } catch (_: IOException) {
            // already closed or broken
        }

        try {
            pipe?.close()
        } catch (_: IOException) {
            // already closed or broken
        }
    }

    // endregion

    private companion object {

        private val CrLf = "\r\n".toByteArray(Charsets.US_ASCII)

        private val Terminator = "\r\n\r\n".toByteArray(Charsets.US_ASCII)

        private fun toArray(buffer: ByteBuffer): ByteArray {
            val result = ByteArray(buffer.remaining())

            buffer.duplicate().get(result)

            return result
        }

        private fun parse(url: String): Parsed {
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("Invalid URL: $url", e)
            }

            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host

            if (scheme == null || host == null) {
                throw IllegalArgumentException("Invalid URL: $url")
            }

            val secure = scheme == "https" || scheme == "wss"

            val defaultPort = when (scheme) {
                "https", "wss" -> 443
                "http", "ws" -> 80
                else -> 0
            }

            val isDefaultPort = uri.port == -1 || uri.port == defaultPort

            val port = if (!isDefaultPort) uri.port else defaultPort

            var route = uri.rawPath

            if (route.isNullOrEmpty()) {
                route = "/"
            }

            if (uri.rawQuery != null) {
                route += "?" + uri.rawQuery
            }

            return Parsed(host, port, isDefaultPort, secure, route.toByteArray(Charsets.US_ASCII))
        }

    }

    private data class Parsed(
        val host: String,
        val port: Int,
        val isDefaultPort: Boolean,
        val secure: Boolean,
        val route: ByteArray
    )

}
