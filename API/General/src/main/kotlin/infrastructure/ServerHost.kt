package org.genhttp.api.infrastructure

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder

import org.slf4j.ILoggerFactory

import java.net.InetAddress

/**
 * Allows to configure, build and manage the lifecycle of a server instance.
 */
public interface ServerHost {

    // region Get-/Setters

    /**
     * The server instance maintained by the host, if started.
     */
    public val instance: Server?

    // endregion

    // region Binding

    /**
     * Specifies the port, the server will listen on (defaults to 8080 on IPv4/IPv6).
     *
     * If you register custom endpoints using the [bind] functions, this value will be ignored.
     *
     * @param port The port the server should listen on
     */
    public fun port(port: Int): ServerHost

    /**
     * Registers an endpoint for the given address and port the server will bind to on startup to listen for
     * incoming HTTP requests.
     *
     * @param address The address to bind to (or null, if the server should listen to any IP)
     * @param port The port to listen on
     * @param httpProtocols The protocols to be accepted by this endpoint
     * @param dualStack If enabled, the endpoint will listen for both IPv4 and IPv6 connections
     */
    public fun bind(address: InetAddress?, port: Int, httpProtocols: HttpProtocols = HttpProtocols.Http1, dualStack: Boolean = true): ServerHost

    /**
     * Registers a secure endpoint the server will bind to on startup to listen for incoming HTTPS requests.
     *
     * @param address The address to bind to (or null, if the server should listen to any IP)
     * @param port The port to listen on
     * @param certificateProvider The provider to select the certificate used to negotiate a connection with
     * @param sslProtocols The TLS protocols which are allowed to be used
     * @param certificateValidator Optional validator to authenticate clients by their certificates
     * @param httpProtocols The protocols to be accepted by this endpoint
     * @param dualStack If enabled, the endpoint will listen for both IPv4 and IPv6 connections
     */
    public fun bind(
        address: InetAddress?,
        port: Int,
        certificateProvider: CertificateProvider,
        sslProtocols: Set<SslProtocol> = setOf(SslProtocol.Tls12, SslProtocol.Tls13),
        certificateValidator: CertificateValidator? = null,
        httpProtocols: HttpProtocols = HttpProtocols.Http1AndHttp2,
        dualStack: Boolean = true
    ): ServerHost

    // endregion

    // region Infrastructure

    /**
     * Enables or disables the development mode on the server instance.
     *
     * When in development mode, the server will return additional information useful for developers of web
     * applications. By default, the development mode is disabled.
     *
     * @param developmentMode Whether the development should be active
     */
    public fun development(developmentMode: Boolean = true): ServerHost

    /**
     * Configures how this server instance will log status changes and handled requests.
     *
     * By default, the server will log to console and install a concern that will log every handled request.
     *
     * @param loggerFactory The logger factory to be used (pass a [org.slf4j.helpers.NOPLoggerFactory] to disable logging)
     * @param logRequests Specifies whether handled requests should be logged
     */
    public fun logging(loggerFactory: ILoggerFactory, logRequests: Boolean = true): ServerHost

    // endregion

    // region Content

    /**
     * Specifies the root handler that will be invoked when a client request needs to be handled.
     *
     * Note that only a single handler is supported. To build are more complex application, consider passing a
     * layout instead.
     *
     * @param handler The handler to be invoked to handle requests
     */
    public fun handler(handler: Handler): ServerHost

    /**
     * Specifies the root handler that will be invoked when a client request needs to be handled.
     *
     * Note that only a single handler is supported. To build are more complex application, consider passing a
     * layout instead.
     *
     * @param handlerBuilder The handler to be invoked to handle requests
     */
    public fun handler(handlerBuilder: HandlerBuilder): ServerHost = handler(handlerBuilder.build())

    /**
     * Adds a concern to the server instance which will be executed before and after the root handler is invoked.
     *
     * @param concern The concern to be added to the instance
     */
    public fun add(concern: ConcernBuilder): ServerHost

    // endregion

    // region Lifecycle

    /**
     * Builds a server instance from the current configuration and starts it.
     */
    public suspend fun start(): ServerHost

    /**
     * Stops the currently running server instance, if any.
     */
    public suspend fun stop(): ServerHost

    /**
     * Stops the currently running server instance and starts a new one.
     */
    public suspend fun restart(): ServerHost

    /**
     * Builds a server instance from the current configuration and keeps it running until the application
     * process exits.
     *
     * Convenience function that can be used as a one-liner by console or docker based applications.
     *
     * @return The return code to be passed to the operating system
     */
    public suspend fun run(): Int

    // endregion

}
