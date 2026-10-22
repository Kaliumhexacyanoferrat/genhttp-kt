package org.genhttp.benchmarks.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.io.Resource
import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString

@State(Scope.Benchmark)
class SimpleBenchmark {

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkSimple() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val context = BenchmarkContext(
            "GET / HTTP/1.1\r\nHost: localhost:8080\r\n\r\n",
            Content.from(Resource.fromString("Hello World")).build()
        )

        context.prepare()

        return context
    }

}
