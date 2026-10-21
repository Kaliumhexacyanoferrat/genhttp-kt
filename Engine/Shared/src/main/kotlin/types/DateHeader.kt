package org.genhttp.engine.shared.types

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Provides the rendered `Date` header line, refreshed once per second.
 */
public object DateHeader {

    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)

    @Volatile
    private var cached: Pair<Long, ByteArray> = -1L to ByteArray(0)

    /**
     * Returns the `Date` header line ("Date: ...\r\n") for the current second.
     */
    public fun getValue(): ByteArray {
        val now = Instant.now()
        val second = now.epochSecond

        val current = cached

        if (current.first == second) {
            return current.second
        }

        val value = "Date: ${format.format(now)}\r\n".toByteArray(Charsets.US_ASCII)
        cached = second to value

        return value
    }

}
