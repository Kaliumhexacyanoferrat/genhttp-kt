package org.genhttp.api.infrastructure

/**
 * Identifies the engine used by a server instance.
 *
 * The engine field allows modules to be optimized for a specific engine implementation. In general, modules must
 * provide their functionality independently of the underlying engine and must not throw exceptions for engines
 * they do not know. Therefore, it is recommended not to rely on this field in business logic.
 *
 * GenHTTP additionally knows `Kestrel` and `Ioxide`, which are .NET specific and not available on the JVM. The
 * counterpart of `Ioxide` is [Netty].
 */
public enum class ServerEngine {

    /** The internal engine of genhttp-kt. */
    Internal,

    /**
     * The engine based on Netty (port of the ioxide engine of GenHTTP), running one event loop per CPU core that
     * accepts and serves its connections.
     */
    Netty,

    /** A custom implementation that is not shipped with the default SDK. */
    Custom

}
