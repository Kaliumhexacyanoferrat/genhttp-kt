package org.genhttp.engine.netty.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.WritableByteChannel
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock

/**
 * Passes the data written by a connection (as the channel of its `ConnectionWriter`) to Netty.
 *
 * Data is copied into buffers of the channel's allocator. Writes on the event loop never wait: the buffers are
 * queued in the outbound buffer of the channel, which grows as needed, and flushed once the loop has processed its
 * current events (so that pipelined responses are sent with a single flush) or as soon as [FlushThreshold] bytes are
 * pending. Writes from other threads (upgraded connections served on virtual threads) are flushed immediately and
 * wait while the channel is not writable, so that a client that does not read cannot exhaust the memory.
 *
 * Writes from other threads reach the channel through the task queue of the loop. While such writes are pending,
 * writes on the loop are queued as well, so that the data is sent in the order it has been written.
 *
 * @param sendTimeout Closes the connection if written data could not be sent for this long (milliseconds, 0 disables it)
 */
internal class OutboundChannel(private val context: ChannelHandlerContext, private val sendTimeout: Long) : WritableByteChannel {

    private val channel = context.channel()

    private val loop = channel.eventLoop()

    private var unflushed = 0

    private var flushScheduled = false

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

    // region Functionality

    override fun write(src: ByteBuffer): Int {
        val onLoop = loop.inEventLoop()

        if (!onLoop) {
            awaitWritable()
        }

        if (!channel.isActive) {
            throw ClosedChannelException()
        }

        val count = src.remaining()

        val buffer = context.alloc().ioBuffer(count)
        buffer.writeBytes(src)

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

        return count
    }

    private fun writeOnLoop(buffer: ByteBuf, count: Int) {
        context.write(buffer, context.voidPromise())

        unflushed += count

        if (unflushed >= FlushThreshold) {
            flush()
        } else if (!flushScheduled) {
            defer(null, flushTask)
            flushScheduled = true
        }
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
        if (loop.inEventLoop() && deferred.get() == 0) {
            closeAfterSend()
        } else {
            try {
                loop.execute { closeAfterSend() }
            } catch (_: RejectedExecutionException) {
                // the connections are closed by the shutdown of the loop
            }
        }
    }

    private fun closeAfterSend() {
        unflushed = 0

        if (channel.isActive) {
            context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        } else {
            channel.close()
        }
    }

    // endregion

    // region Backpressure

    private fun awaitWritable() {
        if (channel.isWritable) {
            return
        }

        val deadline = if (sendTimeout > 0) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sendTimeout) else Long.MAX_VALUE

        lock.withLock {
            while (!channel.isWritable && channel.isActive) {
                if (System.nanoTime() >= deadline) {
                    channel.close()
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
        lock.withLock { writable.signalAll() }
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

        // the marker is written after the pending data, so it completes when all of it has been sent
        val timer = loop.schedule({ channel.close() }, sendTimeout, TimeUnit.MILLISECONDS)

        sendTimer = timer

        context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener {
            timer.cancel(false)
            sendTimer = null
        }
    }

    // endregion

    override fun isOpen(): Boolean = channel.isActive

    override fun close() {
        // owned by the connection
    }

    private companion object {

        /**
         * The number of pending bytes that is flushed immediately instead of after the current events.
         */
        const val FlushThreshold = 64 * 1024

    }

}
