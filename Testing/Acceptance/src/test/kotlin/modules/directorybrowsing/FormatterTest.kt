package org.genhttp.testing.acceptance.modules.directorybrowsing

import org.genhttp.modules.directorybrowsing.provider.FileSizeFormatter

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class FormatterTest {

    @Test
    fun testFormatting() {
        assertEquals("512 Bytes", FileSizeFormatter.format(512))

        assertEquals("2.78 KB", FileSizeFormatter.format(2842))

        assertEquals("2.78 MB", FileSizeFormatter.format((2842 * 1024).toLong()))

        assertEquals("2.78 GB", FileSizeFormatter.format(2842L * 1024 * 1024))

        assertEquals("2.78 TB", FileSizeFormatter.format(2842L * 1024 * 1024 * 1024))
    }

}
