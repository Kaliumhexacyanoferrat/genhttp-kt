package org.genhttp.engine.netty.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoop
import io.netty.util.concurrent.FastThreadLocalThread

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

import org.genhttp.engine.shared.io.WriteTarget

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock
import kotlin.coroutines.resume

/**
 * Provides the buffers the `ConnectionWriter` of a connection collects its data in and passes them to Netty.
 *
 * The buffers are rented from the allocator of the channel, so the collected data is handed over without being
 * copied (only data that is written past the buffer, e.g. large chunks of a body, is copied). Writes on the event
 * loop never wait: the buffers are queued in the outbound buffer of the channel, which grows as needed, and flushed
 * when the loop completes the read the responses have been written for (so that pipelined responses are sent with
 * a single flush), after the current events if they have been written outside a read, or as soon as [FlushThreshold]
 * bytes are pending. Instead, the connection stops reading while the channel is not writable ([awaitWritable]), so
 * that a client that sends requests or frames without reading the answers cannot exhaust the memory. Writes from
 * other threads (upgraded connections served on virtual threads) are flushed immediately and wait while the channel
 * is not writable - unless they are issued by another event loop, which must not be blocked either.
 *
 * Writes from other threads reach the channel through the task queue of the loop. While such writes are pending,
 * writes on the loop are queued as well, so that the data is sent in the order it has been written.
 *
 * @param sendTimeout Closes the connection if written data could not be sent for this long (milliseconds, 0 disables it)
 */
internal class OutboundChannel(private val sendTimeout: Long) : WriteTarget {

    private var _context: ChannelHandlerContext? = null

    private val context: ChannelHandlerContext
        get() = _context ?: throw IllegalStateException("The outbound channel is not bound to a connection")

    private var _channel: Channel? = null

    private val channel: Channel
        get() = _channel ?: throw IllegalStateException("The outbound channel is not bound to a connection")

    private var _loop: EventLoop? = null

    private val loop: EventLoop
        get() = _loop ?: throw IllegalStateException("The outbound channel is not bound to a connection")

    // the buffer the writer currently collects data in (one at a time)
    private var rented: ByteBuf? = null

    private var unflushed = 0

    private var flushScheduled = false

    // whether the loop is delivering received data, which is followed by a flush when the read completes
    private var reading = false

    private var sendTimer: ScheduledFuture<*>? = null

    private val flushTask = Runnable {
        flushScheduled = false
        flush()
    }

    // writes that have been handed to the task queue of the loop but have not been passed to the channel yet
    private val deferred = AtomicInteger()

    private val deferredWritten = ChannelFutureListener { deferred.decrementAndGet() }

    private val lock = ReentrantLock()

    private val writable = lock.newCondition()

    // the coroutine of the connection waiting for the channel to become writable (guarded by the lock)
    private var suspended: CancellableContinuation<Unit>? = null

    // region Lifecycle

    /**
     * Binds the instance to the channel of the given context (on the event loop of the channel).
     */
    fun bind(context: ChannelHandlerContext) {
        _context = context
        _channel = context.channel()
        _loop = context.channel().eventLoop()
    }

    /**
     * Resets the state, so that the instance can be bound to another channel (on the event loop).
     *
     * @return false, if writes of another thread are still pending, so that the instance cannot be reused
     */
    fun reset(): Boolean {
        if (deferred.get() != 0) {
            return false
        }

        rented?.release()
        rented = null

        unflushed = 0
        flushScheduled = false
        reading = false

        sendTimer?.cancel(false)
        sendTimer = null

        lock.withLock { suspended = null }

        _context = null
        _channel = null

        return true
    }

    // endregion

    // region Functionality

    override val isOpen: Boolean
        get() = channel.isActive

    override fun rent(capacity: Int): ByteBuffer {
        val buffer = context.alloc().ioBuffer(capacity, capacity)

        rented = buffer

        return buffer.nioBuffer(0, capacity)
    }

    override fun send(buffer: ByteBuffer) {
        val rent = rented ?: throw IllegalStateException("No buffer has been rented")

        rented = null

        rent.writerIndex(buffer.limit())

        dispatch(rent)
    }

    override fun release(buffer: ByteBuffer) {
        rented?.release()
        rented = null
    }

    override fun write(data: ByteBuffer) {
        // buffers larger than the chunks of the allocator are expensive to allocate, so the data is split
        while (data.hasRemaining()) {
            val count = minOf(data.remaining(), BufferSize)

            val buffer = context.alloc().ioBuffer(count, count)

            val chunk = data.slice(data.position(), count)
            buffer.writeBytes(chunk)
            data.position(data.position() + count)

            dispatch(buffer)
        }
    }

    /**
     * Passes the given buffer to the channel (takes over the reference).
     */
    private fun dispatch(buffer: ByteBuf) {
        val onLoop = loop.inEventLoop()

        try {
            if (!onLoop && Thread.currentThread() !is FastThreadLocalThread) {
                // blocking another event loop (e.g. a websocket broadcasting to the connections of other loops) would
                // halt its connections, so the write is queued instead
                blockUntilWritable()
            }

            if (!channel.isActive) {
                throw ClosedChannelException()
            }
        } catch (e: ClosedChannelException) {
            buffer.release()
            throw e
        }

        val count = buffer.readableBytes()

        if (!onLoop) {
            deferred.incrementAndGet()
            channel.writeAndFlush(buffer).addListener(deferredWritten)
        } else if (deferred.get() > 0) {
            deferred.incrementAndGet()

            try {
                defer(buffer) {
                    deferred.decrementAndGet()
                    writeOnLoop(buffer, count)
                }
            } catch (e: ClosedChannelException) {
                deferred.decrementAndGet()
                throw e
            }
        } else {
            writeOnLoop(buffer, count)
        }
    }

    private fun writeOnLoop(buffer: ByteBuf, count: Int) {
        context.write(buffer, context.voidPromise())

        unflushed += count

        if (unflushed >= FlushThreshold) {
            flush()
        } else if (!reading && !flushScheduled) {
            // written outside a read (e.g. resumed by a timer), so nothing else is going to flush
            defer(null, flushTask)
            flushScheduled = true
        }
    }

    /**
     * Notifies about received data that is going to be delivered (on the event loop).
     */
    fun readStarted() {
        reading = true
    }

    /**
     * Sends the responses written while the received data has been processed (on the event loop).
     */
    fun readCompleted() {
        reading = false
        flush()
    }

    private fun defer(buffer: ByteBuf?, task: Runnable) {
        try {
            loop.execute(task)
        } catch (_: RejectedExecutionException) {
            // the server is shutting down, which closes the connection
            buffer?.release()
            throw ClosedChannelException()
        }
    }

    /**
     * Sends the data written so far (on the event loop).
     */
    fun flush() {
        if (unflushed == 0) {
            return
        }

        unflushed = 0

        context.flush()

        if (sendTimeout > 0) {
            watchSend()
        }
    }

    /**
     * Sends the data written so far and closes the connection as soon as it has been sent (on any thread).
     */
    fun closeWhenSent() {
        val target = context

        if (loop.inEventLoop() && deferred.get() == 0) {
            unflushed = 0
            closeAfterSend(target)
        } else {
            try {
                // the instance may already serve another connection when the task runs
                loop.execute { closeAfterSend(target) }
            } catch (_: RejectedExecutionException) {
                // the connections are closed by the shutdown of the loop
            }
        }
    }

    /**
     * Closes the connection without sending the data that is still pending (on any thread).
     */
    fun abort() {
        _channel?.let(::abort)
    }

    private fun abort(target: Channel) {
        // closing from the head of the pipeline skips the TLS handler, which would wait for the close notification to
        // be sent first - to a client that does not read
        val first = target.pipeline().firstContext()

        if (first != null) {
            first.close()
        } else {
            target.close()
        }
    }

    private fun closeAfterSend(target: ChannelHandlerContext) {
        if (target.channel().isActive) {
            target.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        } else {
            target.close()
        }
    }

    // endregion

    // region Backpressure

    /**
     * Suspends while the channel is not writable (the client does not read the data sent to it), so that the
     * connection does not receive more requests or frames than it can answer (on any thread).
     */
    suspend fun awaitWritable() {
        val channel = channel

        if (channel.isWritable || !channel.isActive) {
            return
        }

        if (loop.inEventLoop()) {
            // data that has not been flushed is never sent, so the channel would not become writable
            flush()
        }

        while (!channel.isWritable && channel.isActive) {
            suspendCancellableCoroutine { continuation ->
                val ready = lock.withLock {
                    if (channel.isWritable || !channel.isActive) {
                        true
                    } else {
                        suspended = continuation
                        false
                    }
                }

                if (ready) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun blockUntilWritable() {
        if (channel.isWritable) {
            return
        }

        val deadline = if (sendTimeout > 0) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sendTimeout) else Long.MAX_VALUE

        lock.withLock {
            while (!channel.isWritable && channel.isActive) {
                if (System.nanoTime() >= deadline) {
                    abort(channel)
                    break
                }

                writable.await(100, TimeUnit.MILLISECONDS)
            }
        }

        if (!channel.isActive) {
            throw ClosedChannelException()
        }
    }

    /**
     * Wakes up writers waiting for the channel to become writable (on the event loop).
     */
    fun writabilityChanged() {
        val channel = _channel ?: return

        val waiting = lock.withLock {
            writable.signalAll()

            if (channel.isWritable || !channel.isActive) suspended.also { suspended = null } else null
        }

        waiting?.resume(Unit)
    }

    // endregion

    // region Send timeout

    private fun watchSend() {
        if (sendTimer != null) {
            return
        }

        val pending = channel.unsafe().outboundBuffer()?.totalPendingWriteBytes() ?: 0

        if (pending == 0L) {
            return
        }

        val target = channel

        // the marker is written after the pending data, so it completes when all of it has been sent
        val timer = loop.schedule({ abort(target) }, sendTimeout, TimeUnit.MILLISECONDS)

        sendTimer = timer

        context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener {
            timer.cancel(false)

            if (sendTimer === timer) {
                sendTimer = null
            }
        }
    }

    // endregion

    companion object {

        /**
         * The size of the buffers rented from the allocator (larger buffers are not pooled by the adaptive
         * allocator of Netty and cost about 9 µs instead of 0.2 µs to allocate).
         */
        const val BufferSize: Int = 16 * 1024

        /**
         * The number of pending bytes that is flushed immediately instead of after the current events.
         */
        private const val FlushThreshold = 64 * 1024

    }

}
