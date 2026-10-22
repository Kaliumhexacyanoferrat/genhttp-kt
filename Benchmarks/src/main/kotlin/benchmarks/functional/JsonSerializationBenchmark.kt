package org.genhttp.benchmarks.benchmarks.functional

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.benchmarks.infrastructure.BenchmarkContext
import org.genhttp.modules.compression.CompressedContent
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.*

import java.time.LocalDateTime
import java.util.UUID

import kotlin.random.Random

data class SomeItem(val id: UUID, val stringValue: String, val intValue: Int, val dateValue: LocalDateTime)

@State(Scope.Benchmark)
class JsonSerializationBenchmark {

    private lateinit var context: BenchmarkContext

    @Setup
    fun setup() = runBlocking {
        context = createContext()
    }

    @Benchmark
    fun benchmarkReturnedJson() = runBlocking { context.execute() }

    private suspend fun createContext(): BenchmarkContext {
        val list = mutableListOf<SomeItem>()

        for (i in 0 until 500) {
            list.add(SomeItem(UUID.randomUUID(), "Some string", Random.nextInt(), LocalDateTime.now()))
        }

        val handler = Inline.create()
            .get { -> list }
            .add(CompressedContent.default())

        val request = "GET / HTTP/1.1\r\nHost: localhost:8080\r\nAccept-Encoding: br;q=1, gzip;q=0.8\r\n\r\n"

        val context = BenchmarkContext(request, handler.build())

        context.prepare()

        return context
    }

}
