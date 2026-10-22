package org.genhttp.engine.internal.infrastructure

import java.io.IOException
import java.nio.channels.Channel
import java.util.concurrent.atomic.AtomicLong

import kotlin.time.Duration

/**
 * The read deadline of a single connection, enforced by a [ConnectionWatchdog].
 *
 * The connection calls [arm] before it blocks to wait for data and [disarm] as soon as the read returned.
 * The watchdog closes the connection if the deadline passes in between. Both sides race for the state of the
 * watch, so a connection is either closed by the watchdog or continues to process the data it read, never both.
 */
internal class ConnectionWatch(private val watchdog: ConnectionWatchdog, private val connection: Channel) {

    private val state = AtomicLong(Idle)

    /**
     * Whether the connection has been closed by the watchdog.
     */
    val expired: Boolean
        get() = state.get() == Expired

    // region Functionality

    /**
     * Marks the connection as waiting for data that needs to arrive before the given deadline
     * (as of [System.nanoTime]).
     *
     * @return false, if the connection has been closed by the watchdog and must not be used anymore
     */
    fun arm(deadline: Long): Boolean {
        if (!state.compareAndSet(Idle, deadline)) {
            return false
        }

        if (watchdog.closed) {
            // the server shut down while the connection was busy
            expireIfWaiting()
            return false
        }

        return true
    }

    /**
     * Marks the connection as no longer waiting for data.
     *
     * @return false, if the connection has been closed by the watchdog in the meantime
     */
    fun disarm(): Boolean {
        val current = state.get()

        // only the watchdog changes the state concurrently, and only to expired
        return current != Expired && state.compareAndSet(current, Idle)
    }

    internal fun expireIfOverdue(now: Long) {
        val current = state.get()

        if (current != Idle && current != Expired && now - current >= 0) {
            expire(current)
        }
    }

    internal fun expireIfWaiting() {
        val current = state.get()

        if (current != Idle && current != Expired) {
            expire(current)
        }
    }

    private fun expire(expected: Long) {
        if (state.compareAndSet(expected, Expired)) {
            try {
                connection.close()
            } catch (_: IOException) {
                // the blocked read fails either way
            }
        }
    }

    // endregion

    companion object {

        private const val Idle = Long.MAX_VALUE

        private const val Expired = Long.MIN_VALUE

        /**
         * Calculates the deadline for data that needs to arrive within the given timeout from now.
         */
        fun deadlineAfter(timeout: Duration): Long = System.nanoTime() + timeout.inWholeNanoseconds

    }

}
