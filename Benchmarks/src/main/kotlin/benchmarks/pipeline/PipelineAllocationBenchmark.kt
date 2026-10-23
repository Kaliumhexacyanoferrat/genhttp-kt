package org.genhttp.benchmarks.benchmarks.pipeline

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.benchmarks.benchmarks.webservices.Baseline
import org.genhttp.benchmarks.infrastructure.BenchmarkServer
import org.genhttp.engine.internal.context.ConnectionContext
import org.genhttp.engine.internal.infrastructure.ConnectionWatchdog
import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.glyph11.parser.ultrahardened.UltraHardenedParser
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.webservices.ServiceResource

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

/**
 * Splits the GET baseline request into stages to see what each of them costs, in particular how much it allocates
 * (run the JMH jar with `-prof gc`, see `gc.alloc.rate.norm`).
 *
 * Unlike [org.genhttp.benchmarks.benchmarks.webservices.BaselineBenchmark], the pipeline is invoked without
 * `runBlocking` (it completes without suspending, like an awaited `ValueTask` in GenHTTP) and the request is replayed
 * from a channel that does not allocate, so that only the allocations of the server remain. Invoking a suspending
 * function this way costs one object ([harnessDirectCall]), which has to be subtracted.
 */
@State(Scope.Benchmark)
class PipelineAllocationBenchmark {

    private lateinit var empty: Pipeline

    private lateinit var content: Pipeline

    private lateinit var baseline: Pipeline

    @Setup
    fun setup() = runBlocking {
        empty = Pipeline(EmptyHandler).also { it.prepare() }
        content = Pipeline(ContentHandler).also { it.prepare() }
        baseline = Pipeline(ServiceResource.from<Baseline>().build()).also { it.prepare() }
    }

    /** Replaying the request into the reader of the connection. */
    @Benchmark
    fun harness() = baseline.harness()

    /** Replaying the request, invoked like the suspending stages. */
    @Benchmark
    fun harnessDirectCall() = baseline.directHarness()

    /** Parsing the request header. */
    @Benchmark
    fun parse() = baseline.parse()

    /** The engine with a handler responding without content. */
    @Benchmark
    fun engineEmptyResponse() = empty.direct()

    /** The engine with a handler responding with a shared string content. */
    @Benchmark
    fun engineStringContent() = content.direct()

    /** The GET baseline of [org.genhttp.benchmarks.benchmarks.webservices.BaselineBenchmark]. */
    @Benchmark
    fun webserviceBaseline() = baseline.direct()

    /** The GET baseline, invoked via `runBlocking` like the other benchmarks. */
    @Benchmark
    fun webserviceBaselineRunBlocking() = runBlocking { baseline.execute() }

}

private object EmptyHandler : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response = request.respond().status(ResponseStatus.Ok).build()

}

private object ContentHandler : Handler {

    private val content = StringContent("3")

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response = request.respond().content(content).build()

}

/**
 * The connection of a single client, processing the request of the GET baseline.
 */
class Pipeline(handler: Handler) {

    private val channel = ReplayChannel("GET /?a=1&b=2 HTTP/1.1\r\nHost: localhost:8080\r\n\r\n".toByteArray(Charsets.US_ASCII))

    private val server = BenchmarkServer(handler)

    private val context = ConnectionContext(
        server, Point, channel, channel, ConnectionWatchdog().register(channel), null, InetAddress.getLoopbackAddress()
    )

    // invoked without runBlocking: the pipeline completes synchronously (like an awaited ValueTask upstream)
    private val executeRef: suspend () -> Unit = ::execute

    private val harnessRef: suspend () -> Unit = ::suspendHarness

    suspend fun prepare() {
        server.handler.prepare(server)
    }

    fun harness() {
        channel.rewind()

        val reader = context.reader
        reader.fill()
        reader.consume(reader.available)
    }

    fun directHarness() = invoke(harnessRef)

    fun parse() {
        channel.rewind()

        val reader = context.reader
        reader.fill()

        val bytesRead = UltraHardenedParser.tryExtractFullHeader(reader.peek(), context.request.source, Limits)
        require(bytesRead >= 0)

        reader.consume(reader.available)
        context.request.source.clear()
    }

    fun direct() = invoke(executeRef)

    suspend fun execute() {
        channel.rewind()

        val reader = context.reader
        reader.fill()

        val bytesRead = UltraHardenedParser.tryExtractFullHeader(reader.peek(), context.request.source, Limits)
        require(bytesRead >= 0)

        reader.consume(bytesRead + 1)

        context.request.apply(server, Point, reader, context.remoteAddress, null)

        context.clientHandler.handleRequest(context.request)
        context.writer.flush()

        context.request.drain()
        context.request.reset()

        reader.consume(reader.available)
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun suspendHarness() = harness()

    private fun invoke(block: suspend () -> Unit) {
        check(block.startCoroutineUninterceptedOrReturn(Completion) !== COROUTINE_SUSPENDED) { "Pipeline suspended" }
    }

    private object Completion : Continuation<Unit> {

        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.getOrThrow()
        }

    }

    private companion object {

        val Limits: ParserLimits = ParserLimits.Default

    }

}

/**
 * Provides the same request on every replay and discards everything written to it, without allocating.
 */
class ReplayChannel(private val data: ByteArray) : ByteChannel {

    private var position = 0

    fun rewind() {
        position = 0
    }

    override fun read(dst: ByteBuffer): Int {
        val remaining = data.size - position

        if (remaining == 0) {
            return -1
        }

        val count = minOf(dst.remaining(), remaining)

        dst.put(data, position, count)
        position += count

        return count
    }

    override fun write(src: ByteBuffer): Int {
        val count = src.remaining()
        src.position(src.limit())
        return count
    }

    override fun isOpen(): Boolean = true

    override fun close() {
    }

}

private object Point : EndPoint {

    override val address: InetAddress? = InetAddress.getLoopbackAddress()

    override val port: Int = 8080

    override val protocols: HttpProtocols = HttpProtocols.Http1

    override val dualStack: Boolean = false

    override val secure: Boolean = false

    override fun close() {
    }

}
