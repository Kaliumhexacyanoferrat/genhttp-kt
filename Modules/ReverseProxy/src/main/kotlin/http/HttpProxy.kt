package org.genhttp.modules.reverseproxy.http

import kotlinx.coroutines.future.await

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Forwarding
import org.genhttp.api.protocol.PercentEncoding
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.getForwardings
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.modules.io.hasType
import org.genhttp.modules.reverseproxy.websocket.WebsocketProxy

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.UnresolvedAddressException
import java.util.Locale

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Relays requests to an upstream HTTP server and returns its response to the client.
 *
 * @property upstream The URL of the server to pass requests to (without trailing slash)
 * @param client The client used to communicate with the upstream server
 * @param readTimeout The time allowed for the upstream server to generate a response (the JDK client
 * configures this per request instead of per client)
 */
public class HttpProxy(
    public val upstream: String,
    private val client: HttpClient,
    private val readTimeout: Duration = 60.seconds
) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val upgradeHeader = request.header.headers.getEntry(UpgradeHeader)

        if (upgradeHeader == WebsocketValue) {
            val wsProxy = WebsocketProxy(upstream)
            return wsProxy.handle(request)
        }

        val req = configureRequest(request)

        val resp = try {
            client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).await()
        } catch (e: HttpConnectTimeoutException) {
            // the JDK reports the connect timeout separately, the connection could not be established
            throw ProviderException(ResponseStatus.BadGateway, "Unable to retrieve a response from the gateway.", e)
        } catch (e: HttpTimeoutException) {
            throw ProviderException(ResponseStatus.GatewayTimeout, "The gateway did not respond in time.", e)
        } catch (e: IOException) {
            throw ProviderException(ResponseStatus.BadGateway, "Unable to retrieve a response from the gateway.", e)
        } catch (e: UnresolvedAddressException) {
            throw ProviderException(ResponseStatus.BadGateway, "Unable to retrieve a response from the gateway.", e)
        }

        try {
            return getResponse(resp, request).build()
        } catch (e: Throwable) {
            resp.body().close()
            throw e
        }
    }

    private fun configureRequest(request: Request): HttpRequest {
        val builder = HttpRequest.newBuilder(getRequestUri(request))

        if (readTimeout.isFinite() && readTimeout > Duration.ZERO) {
            builder.timeout(readTimeout.toJavaDuration())
        }

        // important: set the body before the header so we know whether "Content-" related headers can be sent
        val content = request.getBody()?.takeIf { canSendBody(request) }

        val hasBody = content != null

        // the header is replaced by a retained copy when the body is fetched
        val headers = request.header.headers

        for (i in 0 until headers.count) {
            val (name, header) = headers.getStringEntry(i)

            val key = name.toString()
            val value = header.toString()

            if (ReservedRequestHeaders.contains(key.lowercase(Locale.ROOT))) {
                continue
            }

            if (key.startsWith("Content-", ignoreCase = true)) {
                // computed by the HTTP client from the body publisher
                if (hasBody && !key.equals("Content-Length", ignoreCase = true)) {
                    builder.header(key, value)
                }
            } else if (!UnsupportedRequestHeaders.contains(key.lowercase(Locale.ROOT))) {
                builder.header(key, value)
            }
        }

        val method = request.header.method.toString()

        if (content != null) {
            builder.method(method, RequestBody(content, headers.getEntry("Content-Length")?.trim()?.toLongOrNull() ?: -1))
        } else {
            // the JDK client sends "Content-Length: 0" for an empty publisher, which is not allowed for these methods
            when (method) {
                "GET" -> builder.GET()
                "HEAD" -> builder.HEAD()
                "DELETE" -> builder.DELETE()
                else -> builder.method(method, HttpRequest.BodyPublishers.noBody())
            }
        }

        val forwardings = request.header.headers.getForwardings().toMutableList()

        val client = request.client

        forwardings.add(Forwarding(client.address, null, headers.getEntry("Host"), client.protocol))

        builder.header("Forwarded", forwardings.joinToString(", ") { getForwarding(it) })

        return builder.build()
    }

    private fun getRequestUri(request: Request): URI {
        var remaining = request.header.target.asString(decode = false, remainingOnly = true)

        if (remaining.isNotEmpty() && !remaining.startsWith('/')) {
            remaining = "/$remaining"
        }

        return URI.create(upstream + escapePath(remaining) + getQueryString(request))
    }

    private fun getResponse(response: HttpResponse<InputStream>, request: Request): ResponseBuilder {
        val builder = request.respond()

        val status = ResponseStatus.fromCode(response.statusCode())
            ?: throw ProviderException(ResponseStatus.BadGateway, "The gateway responded with an unsupported status (${response.statusCode()}).")

        builder.status(status)

        setHeaders(builder, request, response)

        if (hasBody(response)) {
            builder.content(ClientResponseContent(response))
        }

        return builder
    }

    private fun setHeaders(builder: ResponseBuilder, request: Request, response: HttpResponse<InputStream>) {
        for ((key, values) in response.headers().map()) {
            if (key.startsWith(":") || ReservedResponseHeaders.contains(key.lowercase(Locale.ROOT))) {
                continue
            }

            val value = values.firstOrNull() ?: continue

            if (key.equals("Location", ignoreCase = true)) {
                builder.header(key, rewriteLocation(value, request))
            } else if (key.equals("Set-Cookie", ignoreCase = true)) {
                for (cookie in values) {
                    builder.header(key, cookie)
                }
            } else {
                builder.header(key, value)
            }
        }
    }

    private fun rewriteLocation(location: String, request: Request): String {
        if (location.startsWith(upstream)) {
            val target = request.header.target

            val path = target.asString(decode = false)
            val scoped = target.asString(decode = false, remainingOnly = true)

            val relativePath = if (scoped != "/" && scoped.isNotEmpty()) {
                path.substring(0, path.length - scoped.length)
            } else {
                path.substring(1)
            }

            val protocol = if (request.endPoint.secure) "https://" else "http://"

            val host = request.header.headers.getEntry("Host")

            return location.replace(upstream, protocol + host + relativePath)
        }

        return location
    }

    private companion object {

        private val UpgradeHeader = ByteString("Upgrade")

        private val WebsocketValue = ByteString("websocket")

        // lower case names
        private val ReservedResponseHeaders = setOf(
            "server", "date", "content-encoding", "transfer-encoding", "content-type", "connection", "content-length", "keep-alive"
        )

        // lower case names
        private val ReservedRequestHeaders = setOf(
            "host", "connection", "forwarded", "upgrade-insecure-requests"
        )

        // The JDK client refuses to send these (hop-by-hop or computed by the client). "Expect" is not relayed as the
        // JDK client would wait for the upstream server to answer with "100 Continue" without a timeout.
        private val UnsupportedRequestHeaders = setOf(
            "upgrade", "transfer-encoding", "expect"
        )

        private fun canSendBody(request: Request): Boolean =
            !request.hasType(RequestMethod.Get, RequestMethod.Head, RequestMethod.Options)

        private fun hasBody(response: HttpResponse<InputStream>): Boolean =
            response.headers().firstValue("Content-Type").isPresent && response.statusCode() != ResponseStatus.NoContent.code

        private fun getQueryString(request: Request): String {
            val query = request.header.query

            if (query.count > 0) {
                // setting a key more than once replaces the previous value (while keeping the position)
                val arguments = LinkedHashMap<String, String>()

                for (i in 0 until query.count) {
                    val (key, value) = query.getStringEntry(i)

                    arguments[key.toString()] = urlDecode(value.toString())
                }

                val encoded = arguments.entries.joinToString("&") { (key, value) ->
                    if (key.isEmpty()) urlEncode(value) else "${urlEncode(key)}=${urlEncode(value)}"
                }

                return "?" + encoded.replace("+", "%20").replace("%2b", "+")
            }

            return ""
        }

        private fun urlDecode(value: String): String {
            val decoded = PercentEncoding.decode(ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8)), decodePlus = true)

            return Charsets.UTF_8.decode(decoded).toString()
        }

        private const val Hex = "0123456789abcdef"

        // same output as HttpUtility.UrlEncode (spaces as "+", lower case hex digits)
        private fun urlEncode(value: String): String {
            val result = StringBuilder(value.length + 16)

            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF

                if (c < 0x80 && (c.toChar().isLetterOrDigit() || "-_.!*()".indexOf(c.toChar()) >= 0)) {
                    result.append(c.toChar())
                } else if (c == ' '.code) {
                    result.append('+')
                } else {
                    result.append('%').append(Hex[c shr 4]).append(Hex[c and 0x0F])
                }
            }

            return result.toString()
        }

        // The JDK is stricter than the .NET URI class, so characters that would be rejected are escaped.
        private fun escapePath(path: String): String {
            val result = StringBuilder(path.length + 16)

            var i = 0

            while (i < path.length) {
                val codePoint = path.codePointAt(i)

                val c = codePoint.toChar()

                if (codePoint < 0x80 && (c.isLetterOrDigit() || "-._~!$&'()*+,;=:@/".indexOf(c) >= 0 || isEscape(path, i))) {
                    result.append(c)
                } else {
                    for (byte in String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)) {
                        val value = byte.toInt() and 0xFF
                        result.append('%').append(Hex[value shr 4].uppercaseChar()).append(Hex[value and 0x0F].uppercaseChar())
                    }
                }

                i += Character.charCount(codePoint)
            }

            return result.toString()
        }

        private fun isEscape(path: String, index: Int): Boolean =
            path[index] == '%' && index + 2 < path.length && isHex(path[index + 1]) && isHex(path[index + 2])

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        private fun getForwarding(forwarding: Forwarding): String {
            val result = ArrayList<String>(3)

            forwarding.forAddress?.let {
                result.add(if (it is Inet6Address) "for=[${format(it)}]" else "for=${format(it)}")
            }

            forwarding.by?.let {
                result.add(if (it is Inet6Address) "by=[${format(it)}]" else "by=${format(it)}")
            }

            forwarding.host?.let {
                result.add("host=$it")
            }

            forwarding.protocol?.let {
                result.add("proto=${it.name}")
            }

            return result.joinToString("; ")
        }

        // IPv6 addresses are written in their compressed form, as known from IPAddress.ToString()
        private fun format(address: InetAddress): String {
            if (address !is Inet6Address) {
                return address.hostAddress
            }

            val bytes = address.address

            val groups = IntArray(8) { ((bytes[it * 2].toInt() and 0xFF) shl 8) or (bytes[it * 2 + 1].toInt() and 0xFF) }

            var bestStart = -1
            var bestLength = 0

            var i = 0

            while (i < 8) {
                if (groups[i] == 0) {
                    var end = i

                    while (end < 8 && groups[end] == 0) {
                        end++
                    }

                    if (end - i > bestLength) {
                        bestStart = i
                        bestLength = end - i
                    }

                    i = end
                } else {
                    i++
                }
            }

            if (bestLength < 2) {
                return groups.joinToString(":") { it.toString(16) }
            }

            val head = groups.take(bestStart).joinToString(":") { it.toString(16) }
            val tail = groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }

            return "$head::$tail"
        }

    }

}
