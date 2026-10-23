package org.genhttp.engine.netty.io

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Queues the data received by a connection until the connection reads it (as the channel of its `ConnectionReader`).
 *
 * The event loop appends received buffers via [offer], the connection waits for them via [await] and copies them
 * into its reader via [read]. Reads never wait: the connection only reads after [await] reported data (or the end of
 * the input). While more than [HighWatermark] bytes are queued, the channel stops reading from the socket.
 *
 * After an upgrade ([upgrade]), the connection is served on a virtual thread that reads the remaining input as a
 * stream: [read] then blocks the virtual thread until the loop delivers data or the connection is closed.
 */
internal class InboundChannel : ReadableByteChannel {

    private var _channel: Channel? = null

    private val channel: Channel
        get() = _channel ?: throw IllegalStateException("The inbound channel is not bound to a connection")

    private val queue = ArrayDeque<ByteBuf>()

    private var queued = 0

    private var completed = false

    // the connection waiting for data (resumed inline on the event loop)
    private var waiter: Continuation<Unit>? = null

    // a reader of the upgraded connection waiting without blocking (guarded by the lock)
    private var suspended: CancellableContinuation<Unit>? = null

    // guards the queue once the connection is served on another thread
    @Volatile
    private var blocking = false

    private val lock = ReentrantLock()

    private val arrived = lock.newCondition()

    // region Get-/Setters

    /**
     * Whether there is data that has not been read yet.
     */
    val hasData: Boolean
        get() = queued > 0

    /**
     * Whether the end of the input has been reached (the client shut down its output or the connection is closed).
     */
    val isCompleted: Boolean
        get() = completed

    // endregion

    // region Lifecycle

    /**
     * Binds the instance to the given channel (on the event loop).
     */
    fun bind(channel: Channel) {
        _channel = channel
    }

    /**
     * Releases the queued data and resets the state, so that the instance can be bound to another channel (on the
     * event loop).
     */
    fun reset() {
        clear()

        completed = false
        waiter = null
        suspended = null
        blocking = false
        _channel = null
    }

    // endregion

    // region Event loop

    /**
     * Queues the given buffer received from the client (on the event loop, takes over the reference).
     */
    fun offer(buffer: ByteBuf) {
        if (blocking) {
            val waiting = lock.withLock {
                enqueue(buffer)
                arrived.signalAll()

                suspended.also { suspended = null }
            }

            waiting?.resume(Unit)
            return
        }

        enqueue(buffer)

        val waiting = waiter

        if (waiting != null) {
            waiter = null
            waiting.resume(Unit)
        }
    }

    private fun enqueue(buffer: ByteBuf) {
        if (completed || !buffer.isReadable) {
            buffer.release()
            return
        }

        queue.addLast(buffer)
        queued += buffer.readableBytes()

        if (queued > HighWatermark) {
            channel.config().isAutoRead = false
        }
    }

    /**
     * Marks the end of the input and releases the queued data if the connection has been closed (on the event loop).
     *
     * @param closed true, if the connection has been closed (queued data cannot be processed anymore)
     */
    fun complete(closed: Boolean) {
        if (blocking) {
            val waiting = lock.withLock {
                finish(closed)
                arrived.signalAll()

                suspended.also { suspended = null }
            }

            waiting?.resume(Unit)
            return
        }

        finish(closed)

        val waiting = waiter

        if (waiting != null) {
            waiter = null
            waiting.resume(Unit)
        }
    }

    private fun finish(closed: Boolean) {
        completed = true

        if (closed) {
            clear()
        }
    }

    // endregion

    // region Connection

    /**
     * Checks whether the connection needs to wait for data before it can read and resumes reading from the socket if
     * so (on the event loop).
     */
    fun mustWait(): Boolean {
        if (queued > 0 || completed) {
            return false
        }

        resumeReading()
        return true
    }

    /**
     * Resumes the given continuation as soon as data has been received or the end of the input has been reached (on
     * the event loop, after [mustWait] returned true).
     *
     * The wait is not cancellable: connections end by being closed, which completes the input.
     */
    fun park(continuation: Continuation<Unit>) {
        waiter = continuation
    }

    /**
     * Switches to blocking reads, as the connection is going to be served on a virtual thread (on the event loop).
     */
    fun upgrade() {
        blocking = true
    }

    /**
     * Suspends until data has been received or the end of the input has been reached, after the connection has been
     * upgraded (on any thread). If the caller runs on the event loop, it is resumed inline when the data arrives.
     */
    suspend fun awaitUpgraded() {
        lock.withLock {
            if (queued > 0 || completed) {
                return
            }
        }

        suspendCancellableCoroutine { continuation ->
            val ready = lock.withLock {
                if (queued > 0 || completed) {
                    true
                } else {
                    suspended = continuation
                    resumeReading()
                    false
                }
            }

            if (ready) {
                continuation.resume(Unit)
            }
        }
    }

    override fun read(dst: ByteBuffer): Int {
        if (blocking) {
            lock.withLock {
                if (queued == 0 && !completed && channel.eventLoop().inEventLoop()) {
                    // the data could only be delivered by the loop that would be blocked
                    throw IllegalStateException("Upgraded connections cannot be read blocking on their event loop, use SuspendingInput.readSuspending()")
                }

                while (queued == 0 && !completed) {
                    resumeReading()
                    arrived.await(1, TimeUnit.SECONDS)
                }

                return transfer(dst)
            }
        }

        if (queued == 0 && !completed) {
            // the connection only reads data it has been notified about
            throw IOException("No data has been received from the client yet")
        }

        return transfer(dst)
    }

    private fun transfer(dst: ByteBuffer): Int {
        if (queued == 0) {
            return -1
        }

        var total = 0

        while (dst.hasRemaining()) {
            val buffer = queue.peekFirst() ?: break

            val count = minOf(buffer.readableBytes(), dst.remaining())

            if (dst.hasArray() && !blocking) {
                // the internal view of the buffer avoids allocating a duplicate, it may only be used on the loop
                buffer.internalNioBuffer(buffer.readerIndex(), count).get(dst.array(), dst.arrayOffset() + dst.position(), count)
                buffer.skipBytes(count)
                dst.position(dst.position() + count)
            } else if (dst.hasArray()) {
                buffer.readBytes(dst.array(), dst.arrayOffset() + dst.position(), count)
                dst.position(dst.position() + count)
            } else {
                val target = dst.slice(dst.position(), count)
                buffer.readBytes(target)
                dst.position(dst.position() + count)
            }

            total += count

            if (!buffer.isReadable) {
                queue.removeFirst()
                buffer.release()
            }
        }

        queued -= total

        if (queued <= HighWatermark) {
            resumeReading()
        }

        return total
    }

    private fun resumeReading() {
        val config = channel.config()

        if (!config.isAutoRead && !completed) {
            config.isAutoRead = true
        }
    }

    /**
     * Releases the queued data (on the event loop).
     */
    fun release() {
        if (blocking) {
            lock.withLock { clear() }
        } else {
            clear()
        }
    }

    private fun clear() {
        while (true) {
            val buffer = queue.pollFirst() ?: break
            buffer.release()
        }

        queued = 0
    }

    override fun isOpen(): Boolean = !completed || queued > 0

    override fun close() {
        // owned by the connection
    }

    // endregion

    private companion object {

        /**
         * The number of queued bytes that stops reading from the socket.
         */
        const val HighWatermark = 64 * 1024

    }

}
