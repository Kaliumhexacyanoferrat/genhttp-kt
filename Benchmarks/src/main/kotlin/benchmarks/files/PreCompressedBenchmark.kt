package org.genhttp.benchmarks.benchmarks.files

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.benchmarks.infrastructure.BenchmarkResources
import org.genhttp.modules.compression.CompressedContent
import org.genhttp.modules.compression.algorithms.BrotliAlgorithm
import org.genhttp.modules.files.Assets

@State(Scope.Benchmark)
class PreCompressedBenchmark {

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkPreCompressedStaticFiles() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val handler = Assets.from(BenchmarkResources.directory)
            .allowPrecompressed(BrotliAlgorithm())
            .add(CompressedContent.default())

        val request = "GET /file.js HTTP/1.1\r\nHost: localhost:8080\r\nAccept-Encoding: br;q=1, gzip;q=0.8\r\n\r\n"

        val context = BenchmarkContext(request, handler.build())

        context.prepare()

        return context
    }

}
