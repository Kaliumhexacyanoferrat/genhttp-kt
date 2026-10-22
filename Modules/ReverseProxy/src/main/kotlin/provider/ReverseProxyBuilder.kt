package org.genhttp.modules.reverseproxy.provider

import org.genhttp.api.content.Concerns
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

import java.net.http.HttpClient

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Creates handlers that pass incoming requests to an upstream server.
 */
public class ReverseProxyBuilder : HandlerBuilder.WithConcerns<ReverseProxyBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var connectTimeout: Duration = 10.seconds

    private var readTimeout: Duration = 60.seconds

    private var upstream: String? = null

    private var handlerAdjustments: ((HttpClient.Builder) -> Unit)? = null

    private var clientAdjustments: ((HttpClient) -> Unit)? = null

    // region Functionality

    /**
     * Sets the server to pass the incoming requests to.
     *
     * @param upstream The URL of the server to pass requests to
     */
    public fun upstream(upstream: String): ReverseProxyBuilder {
        this.upstream = upstream.removeSuffix("/")

        return this
    }

    /**
     * The time allowed to try connecting to the upstream server (defaults to 10 seconds).
     *
     * @param connectTimeout The connection timeout to be set
     */
    public fun connectTimeout(connectTimeout: Duration): ReverseProxyBuilder {
        this.connectTimeout = connectTimeout

        return this
    }

    /**
     * The time allowed for the upstream server to generate a response (defaults to 60 seconds).
     *
     * @param readTimeout The read timeout to be set
     */
    public fun readTimeout(readTimeout: Duration): ReverseProxyBuilder {
        this.readTimeout = readTimeout

        return this
    }

    /**
     * A callback to be invoked to configure the connection handling of the HTTP client used by the handler
     * (the counterpart of `SocketsHttpHandler` on the JVM is the builder of the client).
     *
     * @param adjustment The callback to be invoked
     */
    public fun adjustHandler(adjustment: (HttpClient.Builder) -> Unit): ReverseProxyBuilder {
        handlerAdjustments = adjustment

        return this
    }

    /**
     * A callback to be invoked to configure the HTTP client used by the handler. As the JDK client is
     * immutable, the callback is invoked with the client after it has been created.
     *
     * @param adjustment The callback to be invoked
     */
    public fun adjustClient(adjustment: (HttpClient) -> Unit): ReverseProxyBuilder {
        clientAdjustments = adjustment

        return this
    }

    override fun add(concern: ConcernBuilder): ReverseProxyBuilder {
        concerns.add(concern)

        return this
    }

    override fun build(): Handler {
        val upstream = upstream ?: throw BuilderMissingPropertyException("Upstream")

        val builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout.toJavaDuration())

        handlerAdjustments?.invoke(builder)

        val client = builder.build()

        clientAdjustments?.invoke(client)

        return Concerns.chain(concerns, ReverseProxyProvider(upstream, client, readTimeout))
    }

    // endregion

}
