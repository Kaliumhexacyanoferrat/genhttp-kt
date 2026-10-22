package org.genhttp.benchmarks.benchmarks.webservices

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.engine.shared.infrastructure.CoreRouter
import org.genhttp.engine.shared.infrastructure.ServerCompliance
import org.genhttp.modules.compression.CompressedContent
import org.genhttp.modules.errorhandling.ErrorHandler
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.webservices.addService

/**
 * The baseline requests of HttpArena, served by the same handler chain as the genhttp-kt entry
 * (the concerns added by the host, compression, a layout and a webservice).
 *
 * Not part of the upstream benchmarks.
 */
@State(Scope.Benchmark)
class ArenaBenchmark {

    @Param("get", "post", "chunked")
    var request: String = "get"

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkArena() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val app = Layout.create()
            .addService<Baseline>("baseline11")
            .addService<Baseline>("baseline2")

        val handler = CoreRouter(app.build(), listOf(ServerCompliance.create(), ErrorHandler.default(), CompressedContent.default()))

        val context = BenchmarkContext(Requests.getValue(request), handler)

        context.prepare()

        return context
    }

    private companion object {

        val Requests = mapOf(
            "get" to "GET /baseline11?a=13&b=42 HTTP/1.1\r\nHost: localhost:8080\r\n\r\n",
            "post" to "POST /baseline11?a=13&b=42 HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: 2\r\n\r\n20",
            "chunked" to "POST /baseline11?a=13&b=42 HTTP/1.1\r\nHost: localhost:8080\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n20\r\n0\r\n\r\n"
        )

    }

}
