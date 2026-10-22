package org.genhttp.testing.acceptance.additional.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

import org.genhttp.engine.internal.infrastructure.VirtualThreadExecutor
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The executor schedules the timers of the server dispatcher on sharded timers instead of the single
 * `DefaultExecutor` of kotlinx.coroutines.
 */
class VirtualThreadExecutorTests {

    private fun dispatcher(): ExecutorCoroutineDispatcher =
        VirtualThreadExecutor(Thread.ofVirtual().factory()).asCoroutineDispatcher()

    @Test
    fun testDelayResumesOnVirtualThread() = test {
        withContext(dispatcher()) {
            val start = TimeSource.Monotonic.markNow()

            delay(10.milliseconds)

            assertTrue(start.elapsedNow() >= 10.milliseconds)
            assertTrue(Thread.currentThread().isVirtual)
        }
    }

    @Test
    fun testDelayCanBeCancelled() = test {
        val job = CoroutineScope(dispatcher()).launch {
            delay(1.hours)
        }

        withTimeout(5.seconds) {
            job.cancelAndJoin()
        }

        assertTrue(job.isCancelled)
    }

    @Test
    fun testTimeout() = test {
        withContext(dispatcher()) {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10.milliseconds) {
                    delay(1.hours)
                }
            }

            assertTrue(Thread.currentThread().isVirtual)
        }
    }

    @Test
    fun testManyConcurrentDelays() = test {
        val scope = CoroutineScope(dispatcher())

        val delays = (1..10_000).map {
            scope.async {
                delay(10.milliseconds)
                Thread.currentThread().isVirtual
            }
        }

        withTimeout(30.seconds) {
            assertTrue(delays.awaitAll().all { it })
        }
    }

    @Test
    fun testCallableIsScheduled() {
        val executor = VirtualThreadExecutor(Thread.ofVirtual().factory())

        val future = executor.schedule(Callable { Thread.currentThread().isVirtual }, 10, TimeUnit.MILLISECONDS)

        assertTrue(future.get(5, TimeUnit.SECONDS))
        assertTrue(future.isDone)
    }

    @Test
    fun testCancelledTaskIsNotStarted() {
        val executor = VirtualThreadExecutor(Thread.ofVirtual().factory())
        val started = AtomicBoolean()

        val future = executor.schedule({ started.set(true) }, 50, TimeUnit.MILLISECONDS)

        assertTrue(future.cancel(false))

        Thread.sleep(150)

        assertFalse(started.get())
    }

    @Test
    fun testShutdownRejectsTimers() {
        val executor = VirtualThreadExecutor(Thread.ofVirtual().factory())

        executor.shutdown()

        assertTrue(executor.isShutdown)
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertFailsWith<RejectedExecutionException> {
            executor.schedule({ }, 10, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun testServerUsesExecutor() = test {
        val app = Inline.create().get {
            val dispatcher = currentCoroutineContext()[ContinuationInterceptor]

            assertIs<ExecutorCoroutineDispatcher>(dispatcher)
            assertIs<VirtualThreadExecutor>(dispatcher.executor)

            delay(10.milliseconds)

            if (Thread.currentThread().isVirtual) "virtual" else "platform"
        }

        TestHost.run(app).use { host ->
            assertEquals("virtual", host.getResponse().getContent())
        }
    }

}
