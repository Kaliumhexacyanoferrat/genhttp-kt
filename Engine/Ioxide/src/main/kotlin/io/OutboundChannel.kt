package org.genhttp.engine.ioxide.io

import ioxide.TcpConnection

import kotlinx.coroutines.runBlocking

import org.genhttp.engine.ioxide.infrastructure.ReactorDispatcher
import org.genhttp.engine.ioxide.infrastructure.ReactorState
import org.genhttp.engine.ioxide.security.TlsTransport
import org.genhttp.engine.shared.io.WriteTarget

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Provides the buffers the `ConnectionWriter` of a connection collects its data in and passes the data to the write
 * slab of the connection, which the ring sends from (on the reactor thread).
 *
 * Writes never wait: the data is copied (or encrypted) into the slab, which grows as needed, and sent once the
 * reactor completes the current pass over its completions - so pipelined responses are sent with a single send - or
 * as soon as [FlushThreshold] bytes are staged. A connection sends one buffer at a time: data written while a send is
 * in flight is queued and staged once the kernel has taken the slab. The driver of the connection waits until the
 * data has been sent ([flush]) before it handles more requests of a client that does not read its responses.
 *
 * Writes from other threads (upgraded connections served on virtual threads) are handed to the reactor and wait
 * until they have been sent - unless they are issued by another reactor, which must not be blocked.
 */
internal class OutboundChannel(private val buffers: WriteBufferPool) : WriteTarget {

    private var _connection: TcpConnection? = null

    /**
     * The connection the data is written to.
     */
    val connection: TcpConnection
        get() = _connection ?: throw IllegalStateException("The outbound channel is not bound to a connection")

    private var _dispatcher: ReactorDispatcher? = null

    // counts the connections the instance has been bound to, so that writes handed over for one of them are not written
    // to the next (ioxide reuses its connection objects as well)
    @Volatile
    private var life = 0

    private val dispatcher: ReactorDispatcher
        get() = _dispatcher ?: throw IllegalStateException("The outbound channel is not bound to a connection")

    /**
     * The TLS session of the connection, which encrypts the written data, if any.
     */
    var tls: TlsTransport? = null

    // written while a send was in flight, in the form it is staged in once the send has completed
    private val pending = ArrayDeque<ByteBuffer>()

    private var pendingBytes = 0L

    // a send of the slab is in flight (or about to be)
    private var flushing = false

    // the connection the send in flight belongs to, so that the completion of a send of a previous connection the
    // instance served cannot continue the sends of the next one
    private var flushed: TcpConnection? = null

    // a flush task has been posted to the reactor
    private var scheduled = false

    // whether the target returned by recordTarget is a view on the slab
    private var targetInSlab = false

    // the TLS session of the connection has been ended: nothing may be written (and encrypted) anymore
    private var sealed = false

    // coroutines waiting until everything written has been sent
    private val waiters = ArrayDeque<Continuation<Unit>>()

    // the flush of the bound connection, invoked with a continuation of this instance (no allocation per send)
    private var flushCall: (suspend () -> Unit)? = null

    private val sent = object : Continuation<Unit> {

        override val context: CoroutineContext
            get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            if (flushed === _connection) {
                send()
            }
        }

    }

    private val flushTask = Runnable {
        scheduled = false

        if (_connection != null && !flushing) {
            pump()
        }
    }

    // region Lifecycle

    /**
     * Binds the instance to the given connection (on the reactor thread).
     */
    fun bind(connection: TcpConnection, dispatcher: ReactorDispatcher) {
        _connection = connection
        _dispatcher = dispatcher

        life++

        flushCall = connection::flush
    }

    /**
     * Drops the data that has not been sent and resets the state, so that the instance can be bound to another
     * connection (on the reactor thread).
     */
    fun reset() {
        dropPending()

        flushing = false
        flushed = null
        targetInSlab = false
        sealed = false
        tls = null
        flushCall = null

        _connection = null

        resumeWaiters()
    }

    // endregion

    // region Write target

    override val isOpen: Boolean
        get() = _connection?.isClosed == false

    override fun rent(capacity: Int): ByteBuffer = buffers.rent(capacity)

    override fun send(buffer: ByteBuffer) {
        if (!onReactor()) {
            fromOtherThread(buffer)
            return
        }

        val kept = try {
            submit(buffer, owned = true)
        } catch (e: Exception) {
            buffers.release(buffer)
            throw e
        }

        if (!kept) {
            buffers.release(buffer)
        }
    }

    override fun release(buffer: ByteBuffer) {
        buffers.release(buffer)
    }

    override fun write(data: ByteBuffer) {
        if (!onReactor()) {
            // the caller may reuse the data once the call returns
            fromOtherThread(copyOf(data))
            return
        }

        submit(data, owned = false)
    }

    // endregion

    // region Staging

    /**
     * Whether data can be staged in the slab right now (no send is in flight).
     */
    val canStage: Boolean
        get() = !flushing

    /**
     * The number of bytes that have been written but not sent yet.
     */
    val backlog: Long
        get() = (_connection?.writtenBytes?.toLong() ?: 0L) + pendingBytes

    /**
     * Refuses further writes, as the TLS session of the connection has been ended (its resources may be released):
     * what has been staged in the slab is still sent by [flush], nothing that would need the session.
     */
    fun seal() {
        sealed = true
        tls = null

        // the connection flushed before its session ended, so nothing should be queued; plaintext must never be sent
        dropPending()
    }

    // Stages or queues the content of the given buffer; returns whether the buffer has been queued as it is.
    private fun submit(plaintext: ByteBuffer, owned: Boolean): Boolean {
        val connection = connection

        if (connection.isClosed || sealed) {
            throw ClosedChannelException()
        }

        val tls = tls

        val kept = when {
            tls != null -> {
                tls.encrypt(plaintext, this)
                false
            }

            !flushing -> {
                stage(plaintext)
                false
            }

            owned -> {
                enqueue(plaintext)
                true
            }

            else -> {
                enqueue(copyOf(plaintext))
                false
            }
        }

        written()

        return kept
    }

    /**
     * Copies the content of the given buffer into the slab of the connection (no send may be in flight).
     */
    fun stage(data: ByteBuffer) {
        val connection = connection
        val count = data.remaining()

        if (data.hasArray()) {
            connection.write(data.array(), data.arrayOffset() + data.position(), count)
        } else {
            val target = connection.getSegment(count).asByteBuffer()
            target.put(data.duplicate())
            connection.advance(count)
        }

        data.position(data.limit())
    }

    /**
     * Queues the given buffer, which is staged once the send in flight has completed (it is owned by the channel
     * afterward).
     */
    fun enqueue(buffer: ByteBuffer) {
        if (!buffer.hasRemaining()) {
            return
        }

        pending.addLast(buffer)
        pendingBytes += buffer.remaining()
    }

    /**
     * Returns a buffer of at least the given size an encrypted record can be written to: a view on the slab, or a
     * buffer that is queued by [recordWritten] while a send is in flight.
     */
    fun recordTarget(size: Int): ByteBuffer {
        targetInSlab = !flushing

        return if (targetInSlab) connection.getSegment(size).asByteBuffer() else ByteBuffer.allocate(size)
    }

    /**
     * Commits the given number of bytes written to the buffer returned by [recordTarget].
     */
    fun recordWritten(target: ByteBuffer, count: Int) {
        if (targetInSlab) {
            connection.advance(count)
        } else {
            target.limit(count).position(0)
            enqueue(target)
        }

        written()
    }

    private fun written() {
        if (flushing) {
            // staged by the send in flight once it completes
            return
        }

        if (connection.writtenBytes >= FlushThreshold) {
            pump()
        } else if (!scheduled) {
            // sent once the reactor has handled the current completions, together with what else is written meanwhile
            scheduled = true
            dispatcher.reactor.post(flushTask)
        }
    }

    // endregion

    // region Sending

    /**
     * Suspends until everything that has been written has been sent, i.e. taken by the kernel (on the reactor thread).
     *
     * Returns once the connection has been closed, the data that could not be sent is dropped.
     */
    suspend fun flush() {
        if (_connection == null) {
            return
        }

        if (!flushing) {
            pump()
        }

        if (flushing) {
            suspendCoroutine { waiters.addLast(it) }
        }
    }

    private fun pump() {
        if (connection.writtenBytes == 0 && pending.isEmpty()) {
            return
        }

        flushing = true
        flushed = connection
        send()
    }

    // Sends the slab, stages what has been queued meanwhile once that send completed and sends again, until nothing
    // is left (resumed inline by the ring when a send completed).
    private fun send() {
        val connection = _connection

        while (true) {
            if (connection == null || connection.isClosed) {
                dropPending()
                return finished()
            }

            if (connection.writtenBytes == 0) {
                if (pending.isEmpty()) {
                    return finished()
                }

                stagePending(connection)
            }

            val flush = flushCall ?: return finished()

            if (flush.startCoroutineUninterceptedOrReturn(sent) === COROUTINE_SUSPENDED) {
                return
            }
        }
    }

    private fun stagePending(connection: TcpConnection) {
        val tls = tls

        while (true) {
            val entry = pending.removeFirstOrNull() ?: break

            pendingBytes -= entry.remaining()

            if (tls != null) {
                tls.stageQueued(entry, connection)
            } else {
                stage(entry)
            }

            buffers.release(entry)
        }
    }

    private fun finished() {
        flushing = false
        flushed = null
        resumeWaiters()
    }

    private fun resumeWaiters() {
        while (true) {
            val waiter = waiters.removeFirstOrNull() ?: break
            waiter.resume(Unit)
        }
    }

    private fun dropPending() {
        while (true) {
            buffers.release(pending.removeFirstOrNull() ?: break)
        }

        pendingBytes = 0
    }

    // endregion

    // region Other threads

    private fun onReactor(): Boolean = _dispatcher?.reactor?.onReactorThread ?: true

    private fun fromOtherThread(buffer: ByteBuffer) {
        val target = _connection ?: throw ClosedChannelException()
        val dispatcher = dispatcher
        val current = life

        if (ReactorState.current() != null) {
            // blocking another reactor (e.g. a websocket broadcasting to the connections of other reactors) would halt
            // its connections, so the write is queued instead
            dispatcher.reactor.post {
                if (life == current && _connection === target && !target.isClosed) {
                    try {
                        submit(buffer, owned = true)
                    } catch (_: Exception) {
                        // the connection has been closed meanwhile
                    }
                }
            }

            return
        }

        runBlocking(dispatcher) {
            if (life != current || _connection !== target || !dispatcher.reactor.onReactorThread) {
                throw ClosedChannelException()
            }

            submit(buffer, owned = true)

            flush()

            if (target.isClosed) {
                throw ClosedChannelException()
            }
        }
    }

    private fun copyOf(data: ByteBuffer): ByteBuffer {
        val copy = ByteBuffer.allocate(data.remaining())

        copy.put(data.duplicate()).flip()
        data.position(data.limit())

        return copy
    }

    // endregion

    private companion object {

        /**
         * The number of staged bytes that are sent immediately instead of after the current completions.
         */
        const val FlushThreshold = 64 * 1024

    }

}

/**
 * The heap buffers the connections of a reactor collect the data they write in (on the reactor thread; buffers
 * rented on other threads are not pooled).
 */
internal class WriteBufferPool(private val dispatcher: () -> ReactorDispatcher?) {

    private val idle = ArrayDeque<ByteBuffer>()

    /**
     * Returns an empty buffer of the given capacity (only buffers of [Size] are pooled).
     */
    fun rent(capacity: Int): ByteBuffer {
        if (capacity == Size && onReactor()) {
            idle.removeLastOrNull()?.let { return it }
        }

        return ByteBuffer.allocate(capacity)
    }

    /**
     * Takes back the given buffer, the caller must not use it anymore.
     */
    fun release(buffer: ByteBuffer) {
        if (buffer.capacity() == Size && idle.size < MaxPooled && onReactor()) {
            buffer.clear()
            idle.addLast(buffer)
        }
    }

    private fun onReactor(): Boolean = dispatcher()?.reactor?.onReactorThread ?: false

    companion object {

        /**
         * The size of the buffers the connections write in.
         */
        const val Size: Int = 16 * 1024

        private const val MaxPooled = 256

    }

}
