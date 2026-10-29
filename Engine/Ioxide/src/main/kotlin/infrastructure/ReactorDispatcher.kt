package org.genhttp.engine.ioxide.infrastructure

import ioxide.Reactor
import ioxide.RingTimer

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

/**
 * Runs coroutines on a single reactor.
 *
 * Coroutines that resume on the reactor itself (e.g. because the ring delivered the data they waited for) continue
 * inline, coroutines that resume on other threads (e.g. after `withContext(Dispatchers.IO)`) are handed back to the
 * reactor. Timers (`delay`, `withTimeout`) are submitted to the ring of the reactor (`IORING_OP_TIMEOUT`), so they do
 * not leave it.
 *
 * Once the reactor has been stopped, coroutines are cancelled and complete on [Dispatchers.IO], so that they can
 * still run their cleanup.
 */
@OptIn(InternalCoroutinesApi::class)
internal class ReactorDispatcher(val reactor: Reactor) : CoroutineDispatcher(), Delay {

    @Volatile
    private var stopped = false

    /**
     * Hands coroutines that resume after the reactor has been stopped to [Dispatchers.IO].
     */
    fun stop() {
        stopped = true
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !reactor.onReactorThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (stopped) {
            reject(context)
            Dispatchers.IO.dispatch(context, block)
            return
        }

        reactor.post(block)
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        if (stopped) {
            reject(continuation.context)
            continuation.resume(Unit)
            return
        }

        // a cancelled continuation ignores the resumption once the timer expires
        wait(timeMillis) { continuation.resume(Unit) }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val timeout = Timeout(block)

        if (stopped) {
            reject(context)
            return timeout
        }

        wait(timeMillis, timeout)

        return timeout
    }

    // the ring completes the timer on the reactor thread, which then runs the action inline
    private fun wait(timeMillis: Long, action: Runnable) {
        val timer = RingTimer(reactor)

        suspend { timer.delay(timeMillis) }.startCoroutine(Continuation(EmptyCoroutineContext) { action.run() })
    }

    private fun reject(context: CoroutineContext) {
        context.cancel(CancellationException("The reactor has been stopped"))
    }

    override fun toString(): String = "ReactorDispatcher(${reactor.id})"

    /**
     * A timeout that does not run its block once it has been disposed.
     */
    private class Timeout(private val block: Runnable) : DisposableHandle, Runnable {

        @Volatile
        private var disposed = false

        override fun run() {
            if (!disposed) {
                block.run()
            }
        }

        override fun dispose() {
            disposed = true
        }

    }

}
