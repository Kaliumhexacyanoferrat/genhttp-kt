package org.genhttp.benchmarks.benchmarks.webservices

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.modules.webservices.ServiceResource

@State(Scope.Benchmark)
class BaselineBenchmark {

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkBaseline() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val handler = ServiceResource.from<Baseline>()

        val request = "GET /?a=1&b=2 HTTP/1.1\r\nHost: localhost:8080\r\n\r\n"

        val context = BenchmarkContext(request, handler.build())

        context.prepare()

        return context
    }

}
