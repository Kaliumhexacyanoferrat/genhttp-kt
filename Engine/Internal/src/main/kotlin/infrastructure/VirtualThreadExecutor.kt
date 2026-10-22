package org.genhttp.engine.internal.infrastructure

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Runs every task on a new virtual thread and schedules delayed tasks on timers shared by the whole process.
 *
 * kotlinx.coroutines uses the executor of a dispatcher for timers (`delay`, `withTimeout`) only if it is a
 * [ScheduledExecutorService]. Otherwise, the timers of all coroutines end up in the single, synchronized queue of
 * the `DefaultExecutor` thread, which limits the number of delays that can expire per second and lets suspended
 * handlers wait far longer than requested under load. This executor spreads the timers over several queues (similar
 * to the per-core timer queues of .NET) and starts a new virtual thread as soon as a timer expires, so delayed tasks
 * never run on a timer thread.
 *
 * Periodic tasks are not supported, as kotlinx.coroutines does not use them.
 */
internal class VirtualThreadExecutor(threadFactory: ThreadFactory) : AbstractExecutorService(), ScheduledExecutorService {

    private val threads: ExecutorService = Executors.newThreadPerTaskExecutor(threadFactory)

    // region Functionality

    override fun execute(command: Runnable) {
        threads.execute(command)
    }

    /**
     * Schedules the given command to be started on a new virtual thread after the given delay.
     *
     * Cancelling the returned future prevents the command from being started, but does not interrupt it once it is
     * running.
     */
    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        if (isShutdown) {
            // lets kotlinx.coroutines fall back to its default executor
            throw RejectedExecutionException("Executor has been shut down")
        }

        return Timers.select().schedule({ execute(command) }, delay, unit)
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        val task = FutureTask(callable)

        return RelayedFuture(task, schedule(task, delay, unit))
    }

    override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> =
        throw UnsupportedOperationException("Periodic tasks are not supported")

    override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
        throw UnsupportedOperationException("Periodic tasks are not supported")

    // endregion

    // region Lifecycle

    override fun shutdown() {
        threads.shutdown()
    }

    override fun shutdownNow(): List<Runnable> = threads.shutdownNow()

    override fun isShutdown(): Boolean = threads.isShutdown

    override fun isTerminated(): Boolean = threads.isTerminated

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = threads.awaitTermination(timeout, unit)

    // endregion

    /**
     * A task that is started on a virtual thread as soon as its timer expires.
     */
    private class RelayedFuture<V>(private val task: FutureTask<V>, private val timer: ScheduledFuture<*>) :
        ScheduledFuture<V>, Future<V> by task {

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            timer.cancel(false)
            return task.cancel(mayInterruptIfRunning)
        }

        override fun getDelay(unit: TimeUnit): Long = timer.getDelay(unit)

        override fun compareTo(other: Delayed): Int = timer.compareTo(other)

    }

    /**
     * The timers shared by all servers of the process.
     *
     * Like the timers of virtual threads on custom schedulers in the JDK, there is a queue with a single thread per
     * four processors (but at least two). The thread starts the expired tasks itself, as handing them over to another
     * thread costs more than starting a virtual thread.
     */
    private object Timers {

        private val shards = Array(maxOf(2, Runtime.getRuntime().availableProcessors() / 4)) { index ->
            ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("genhttp-timer-$index").daemon().factory()).apply {
                // timeouts are cancelled most of the time
                removeOnCancelPolicy = true
            }
        }

        fun select(): ScheduledExecutorService = shards[(Thread.currentThread().threadId() % shards.size).toInt()]

    }

}
