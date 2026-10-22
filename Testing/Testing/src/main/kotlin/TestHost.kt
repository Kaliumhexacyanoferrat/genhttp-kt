package org.genhttp.testing

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.internal.Host
import org.genhttp.modules.practices.defaults

import java.net.Authenticator
import java.net.CookieHandler
import java.net.PasswordAuthentication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Hosts a server instance on a free port for the duration of a test, allowing to send requests to it.
 *
 * ```kotlin
 * TestHost.run(handler).use { host ->
 *     val response = host.getResponse("/path")
 *     response.assertStatus(ResponseStatus.Ok)
 * }
 * ```
 *
 * Responses are read completely ([HttpResponse] of [ByteArray]) using the JDK HTTP client, so they do not need
 * to be closed.
 *
 * @param handler The handler to be tested
 * @param defaults Whether the default policies (compression, client caching, ...) should be enabled
 * @param development Whether the server should run in development mode
 * @param engine The engine to run the server with
 */
public open class TestHost(
    handler: Handler,
    defaults: Boolean = true,
    development: Boolean = true,
    engine: ServerEngine = ServerEngine.Internal
) : AutoCloseable {

    /**
     * The port the server is listening on.
     */
    public val port: Int = nextPort()

    /**
     * The host running the server.
     */
    public val host: ServerHost = createHost(engine)

    init {
        host.handler(handler)
        host.port(port)

        if (defaults) {
            host.defaults()
        }

        if (development) {
            host.development()
        }
    }

    // region Functionality

    /**
     * Starts the server.
     */
    public suspend fun start() {
        host.start()
    }

    /**
     * Returns the URL to access the given path on the server.
     */
    public fun getUrl(path: String? = null): String = "http://localhost:$port${path ?: ""}"

    /**
     * Creates a request for the given path, using the given method (GET by default).
     */
    public fun getRequest(path: String? = null, method: String? = null): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(getUrl(path)))
            .timeout(ClientTimeout)

        // the JDK client sends "Content-Length: 0" for methods set with an empty body publisher
        return when (method?.uppercase()) {
            null, "GET" -> builder.GET()
            "HEAD" -> builder.HEAD()
            "DELETE" -> builder.DELETE()
            else -> builder.method(method, HttpRequest.BodyPublishers.noBody())
        }
    }

    /**
     * Requests the given path from the server.
     */
    public suspend fun getResponse(path: String? = null, client: HttpClient? = null): HttpResponse<ByteArray> =
        getResponse(getRequest(path), client)

    /**
     * Sends the given request to the server.
     */
    public suspend fun getResponse(request: HttpRequest.Builder, client: HttpClient? = null): HttpResponse<ByteArray> =
        getResponse(request.build(), client)

    /**
     * Sends the given request to the server.
     */
    public suspend fun getResponse(request: HttpRequest, client: HttpClient? = null): HttpResponse<ByteArray> =
        (client ?: defaultClient).sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

    // endregion

    // region Lifecycle

    /**
     * Stops the server.
     */
    override fun close() {
        runBlocking {
            host.stop()
        }
    }

    // endregion

    /**
     * Factory functions and shared clients.
     */
    public companion object {

        private val ClientTimeout = Duration.ofSeconds(15)

        // can be moved with GENHTTP_TEST_PORT_BASE, e.g. to run several test suites in parallel
        private val nextPort = AtomicInteger(System.getenv("GENHTTP_TEST_PORT_BASE")?.toIntOrNull() ?: 22010)

        init {
            // allows tests to send headers the JDK client restricts by default (e.g. "Host" for virtual hosting)
            if (System.getProperty("jdk.httpclient.allowRestrictedHeaders") == null) {
                System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host,connection,content-length,expect,upgrade")
            }

            // the JDK client cannot skip hostname verification per client, so clients that ignore security errors
            // (see getClient) require it to be disabled for the JVM running the tests
            if (System.getProperty("jdk.internal.httpclient.disableHostnameVerification") == null) {
                System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true")
            }
        }

        private val defaultClient: HttpClient by lazy { getClient() }

        /**
         * Creates and starts a test host for the given handler.
         */
        public suspend fun run(handler: Handler, defaults: Boolean = true, development: Boolean = true, engine: ServerEngine = ServerEngine.Internal): TestHost {
            val runner = TestHost(handler, defaults, development, engine)
            runner.start()
            return runner
        }

        /**
         * Creates and starts a test host for the given handler.
         */
        public suspend fun run(handler: HandlerBuilder, defaults: Boolean = true, development: Boolean = true, engine: ServerEngine = ServerEngine.Internal): TestHost =
            run(handler.build(), defaults, development, engine)

        /**
         * Returns the next free port to be used by a test host.
         */
        public fun nextPort(): Int = nextPort.incrementAndGet()

        /**
         * Creates a HTTP client to send requests to a test host.
         *
         * @param ignoreSecurityErrors Whether invalid server certificates should be accepted
         * @param followRedirects Whether redirects should be followed automatically
         * @param protocolVersion The protocol version to be used (HTTP/1.1 by default)
         * @param creds The credentials to answer authentication challenges with
         * @param cookies The cookie store to be used (cookies are not handled if not set)
         */
        public fun getClient(
            ignoreSecurityErrors: Boolean = false,
            followRedirects: Boolean = false,
            protocolVersion: HttpClient.Version? = null,
            creds: PasswordAuthentication? = null,
            cookies: CookieHandler? = null
        ): HttpClient {
            val builder = HttpClient.newBuilder()
                .version(protocolVersion ?: HttpClient.Version.HTTP_1_1)
                .followRedirects(if (followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
                .connectTimeout(ClientTimeout)

            if (creds != null) {
                builder.authenticator(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication = creds
                })
            }

            if (cookies != null) {
                builder.cookieHandler(cookies)
            }

            if (ignoreSecurityErrors) {
                builder.sslContext(trustAllContext())
            }

            return builder.build()
        }

        private fun createHost(engine: ServerEngine): ServerHost = when (engine) {
            ServerEngine.Internal -> Host.create()
            else -> throw UnsupportedOperationException("Engine '$engine' is not supported")
        }

        private fun trustAllContext(): SSLContext {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

            return SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), SecureRandom())
            }
        }

    }

}
