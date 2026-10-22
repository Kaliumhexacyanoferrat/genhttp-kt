package org.genhttp.modules.directorybrowsing.provider

import java.math.BigDecimal

/**
 * Formats file sizes in a human readable way.
 */
public object FileSizeFormatter {

    private const val KILOBYTES = 1024.0

    private const val MEGABYTES = KILOBYTES * 1024

    private const val GIGABYTES = MEGABYTES * 1024

    private const val TERABYTES = GIGABYTES * 1024

    /**
     * Formats the given number of bytes using the largest unit that fits.
     *
     * @param bytes The number of bytes to be formatted
     * @return The formatted size, or a dash if the size is not known
     */
    public fun format(bytes: Long?): String {
        if (bytes != null) {
            return when {
                bytes > TERABYTES -> round(bytes / TERABYTES) + " TB"
                bytes > GIGABYTES -> round(bytes / GIGABYTES) + " GB"
                bytes > MEGABYTES -> round(bytes / MEGABYTES) + " MB"
                bytes > KILOBYTES -> round(bytes / KILOBYTES) + " KB"
                else -> "$bytes Bytes"
            }
        }

        return "-"
    }

    // like Math.Round(value, 2) (rounds half to even) and the invariant ToString() of .NET
    private fun round(value: Double): String =
        BigDecimal.valueOf(Math.rint(value * 100.0) / 100.0).stripTrailingZeros().toPlainString()

}
