package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.ConnectionClosedException

import java.io.IOException
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CancellationException

/**
 * Helps to distinguish clients that went away (nothing to report) from actual failures.
 */
public object ConnectionExceptions {

    private val disconnectMessages = listOf(
        "broken pipe",
        "connection reset",
        "connection was aborted",
        "connection abort",
        "an established connection was aborted",
        "an existing connection was forcibly closed",
        "socket closed",
        "connection closed"
    )

    /**
     * Checks whether the given exception has been caused by a client closing the connection.
     */
    public fun isGracefulDisconnect(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0

        while (current != null && depth < 8) {
            when (current) {
                is CancellationException -> return true
                is ConnectionClosedException -> return true
                is ClosedChannelException -> return true
                is SocketException, is IOException -> {
                    val message = current.message?.lowercase()

                    if (message != null && disconnectMessages.any { message.contains(it) }) {
                        return true
                    }
                }
            }

            current = current.cause
            depth++
        }

        return false
    }

}
