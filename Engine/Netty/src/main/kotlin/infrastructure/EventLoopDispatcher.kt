package org.genhttp.engine.netty.infrastructure

import io.netty.channel.EventLoop

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonDisposableHandle
import kotlinx.coroutines.cancel

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * Runs coroutines on a single event loop.
 *
 * Coroutines that resume on the loop itself (e.g. because the loop delivered the data they waited for) continue
 * inline, coroutines that resume on other threads (e.g. after `withContext(Dispatchers.IO)`) are handed back to the
 * loop. Timers (`delay`, `withTimeout`) are scheduled on the loop as well, so that they do not leave it.
 *
 * If the loop has been shut down, the coroutine is cancelled and completes on [Dispatchers.IO], so that it can
 * still run its cleanup.
 */
@OptIn(InternalCoroutinesApi::class)
internal class EventLoopDispatcher(val loop: EventLoop) : CoroutineDispatcher(), Delay {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !loop.inEventLoop()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            loop.execute(block)
        } catch (e: RejectedExecutionException) {
            reject(context, e)
            Dispatchers.IO.dispatch(context, block)
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val future = try {
            loop.schedule({ continuation.resume(Unit) }, timeMillis, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            reject(continuation.context, e)
            continuation.resume(Unit)
            return
        }

        continuation.invokeOnCancellation { future.cancel(false) }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val future = try {
            loop.schedule(block, timeMillis, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            reject(context, e)
            return NonDisposableHandle
        }

        return DisposableHandle { future.cancel(false) }
    }

    private fun reject(context: CoroutineContext, cause: RejectedExecutionException) {
        context.cancel(CancellationException("The event loop has been shut down", cause))
    }

    override fun toString(): String = "EventLoopDispatcher($loop)"

}
