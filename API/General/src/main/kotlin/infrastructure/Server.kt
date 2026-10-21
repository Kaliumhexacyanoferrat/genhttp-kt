package org.genhttp.api.infrastructure

import org.genhttp.api.content.Handler

import org.slf4j.ILoggerFactory

/**
 * Listens for incoming HTTP requests and dispatches them to the registered routers and content providers.
 */
public interface Server {

    /**
     * The engine used by this server instance.
     */
    public val serverEngine: ServerEngine

    /**
     * The version of the server software.
     *
     * This property is for informational use only. Do not change your code depending on the version you are
     * working with.
     */
    public val version: String

    /**
     * Specifies, whether the server still serves requests or whether it is currently shut down.
     */
    public val running: Boolean

    /**
     * If enabled, components may provide additional information allowing developers to further debug web
     * applications.
     */
    public val development: Boolean

    /**
     * Property bag to store values during the lifetime of the server.
     */
    public val properties: PropertyBag

    /**
     * The logging infrastructure of the application, used by the server itself as well as by the handlers,
     * concerns and other components running within it to emit diagnostic information.
     *
     * Logs to the console by default. Use [ServerHost.logging] to configure a different factory or to disable
     * logging entirely.
     */
    public val logging: ILoggerFactory

    /**
     * The endpoints the server is listening on.
     */
    public val endPoints: EndPointCollection

    /**
     * The main router that will be used by the server to dispatch incoming HTTP requests.
     */
    public val handler: Handler

    /**
     * Starts to listen for requests, shut down by closing this instance.
     */
    public suspend fun start()

    /**
     * Shuts down the server and releases all resources (`DisposeAsync` in GenHTTP).
     */
    public suspend fun close()

}
