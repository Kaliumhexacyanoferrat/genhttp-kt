package org.genhttp.engine.shared.types

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Provides the rendered `Date` header line, refreshed once per second.
 *
 * The line is rendered by a background thread at the start of every second (like Kestrel does), so that responses
 * do not need to read the clock.
 */
public object DateHeader {

    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)

    @Volatile
    private var current: ByteArray = render(System.currentTimeMillis())

    init {
        Thread.ofPlatform().name("genhttp-date-header").daemon().start {
            while (true) {
                Thread.sleep(1000 - System.currentTimeMillis() % 1000)

                current = render(System.currentTimeMillis())
            }
        }
    }

    /**
     * Returns the `Date` header line ("Date: ...\r\n") for the current second.
     */
    public fun getValue(): ByteArray = current

    private fun render(millis: Long): ByteArray =
        "Date: ${format.format(Instant.ofEpochSecond(millis / 1000))}\r\n".toByteArray(Charsets.US_ASCII)

}
