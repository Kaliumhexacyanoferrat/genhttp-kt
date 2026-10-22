package org.genhttp.benchmarks.infrastructure

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine

import org.slf4j.ILoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

/**
 * A server instance that is not bound to any endpoint and just serves the given handler.
 */
class BenchmarkServer(override val handler: Handler) : Server {

    @Volatile
    private var active = true

    override val serverEngine: ServerEngine = ServerEngine.Custom

    override val version: String = "0.1"

    override val running: Boolean
        get() = active

    override val development: Boolean = false

    override val properties: PropertyBag
        get() = throw UnsupportedOperationException()

    override val logging: ILoggerFactory = NOPLoggerFactory()

    override val endPoints: EndPointCollection
        get() = throw UnsupportedOperationException()

    override suspend fun start() {
        active = true
    }

    override suspend fun close() {
        active = false
    }

}
