package org.genhttp.benchmarks.benchmarks.io

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.benchmarks.infrastructure.BenchmarkResources
import org.genhttp.modules.files.Assets

@State(Scope.Benchmark)
class StaticFileBenchmark {

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkStaticFile() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val handler = Assets.from(BenchmarkResources.directory)

        val request = "GET /file.js HTTP/1.1\r\nHost: localhost:8080\r\n\r\n"

        val context = BenchmarkContext(request, handler.build())

        context.prepare()

        return context
    }

}
