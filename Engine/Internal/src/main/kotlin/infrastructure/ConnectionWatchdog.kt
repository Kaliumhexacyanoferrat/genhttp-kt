package org.genhttp.engine.internal.infrastructure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import java.nio.channels.Channel
import java.util.concurrent.ConcurrentHashMap

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Closes client connections that do not send data in time.
 *
 * GenHTTP cancels pending reads with a cancellation token that expires after the read timeout. Arming a timer for
 * every read does not scale on the JVM (kotlinx.coroutines keeps the timers of executor based dispatchers in a
 * single, synchronized queue), so connections publish a deadline while they wait for data instead and the watchdog
 * checks the deadlines of all connections in a fixed interval. Closing an expired connection aborts its blocked read.
 *
 * @param interval The interval in which deadlines are checked (timeouts fire up to one interval late)
 */
internal class ConnectionWatchdog(private val interval: Duration = DefaultInterval) {

    private val connections: MutableSet<ConnectionWatch> = ConcurrentHashMap.newKeySet()

    /**
     * Set as soon as the watchdog has been closed, so that connections can no longer wait for data.
     */
    @Volatile
    var closed: Boolean = false
        private set

    // region Functionality

    /**
     * Starts to watch the given connection, which needs to be passed to [unregister] as soon as it has been closed.
     */
    fun register(connection: Channel): ConnectionWatch {
        val watch = ConnectionWatch(this, connection)

        connections.add(watch)

        return watch
    }

    /**
     * Stops watching the given connection.
     */
    fun unregister(watch: ConnectionWatch) {
        connections.remove(watch)
    }

    /**
     * Checks the deadlines of the registered connections in the configured interval until the scope is cancelled.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(interval)
            check(System.nanoTime())
        }
    }

    /**
     * Closes all connections whose deadline has passed at the given point in time (as of [System.nanoTime]).
     */
    fun check(now: Long) {
        for (watch in connections) {
            watch.expireIfOverdue(now)
        }
    }

    // endregion

    // region Lifecycle

    /**
     * Closes all connections that currently wait for data. Connections that start to wait later are closed
     * immediately.
     */
    fun close() {
        closed = true

        for (watch in connections) {
            watch.expireIfWaiting()
        }
    }

    // endregion

    private companion object {

        val DefaultInterval = 1.seconds

    }

}
