package org.genhttp.modules.directorybrowsing.provider

import java.text.Collator
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the HTML table listing the content of a resource container.
 */
public object ListingRenderer {

    // the default format of DateTime.ToString() with the invariant culture
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)

    // names are sorted culture-sensitive, like string comparison does in .NET
    private val collator: Collator = Collator.getInstance(Locale.ROOT)

    /**
     * Renders the listing of the given model.
     *
     * @param model The model to be rendered
     * @return The HTML table listing the container
     */
    public suspend fun render(model: ListingModel): String {
        val content = StringBuilder()

        content.appendLine("<table>")

        content.appendLine("<thead>")
        content.appendLine("  <tr>")
        content.appendLine("    <th>Name</th>")
        content.appendLine("    <th>Size</th>")
        content.appendLine("    <th>Last Modified</th>")
        content.appendLine("  </tr>")
        content.appendLine("</thead>")

        if (model.hasParent) {
            append(content, "../", "..", null, null)
        }

        for (dir in model.container.getNodes().sortedWith { a, b -> collator.compare(a.name, b.name) }) {
            append(content, "./${urlPathEncode(dir.name)}/", dir.name, null, dir.modified)
        }

        for (file in model.container.getResources().filter { it.name != null }.sortedWith { a, b -> collator.compare(a.name, b.name) }) {
            append(content, "./${urlPathEncode(file.name!!)}", file.name!!, file.length, file.modified)
        }

        content.appendLine("</table>")

        return content.toString()
    }

    private fun append(builder: StringBuilder, path: String, name: String, size: Long?, modified: Instant?) {
        builder.appendLine("<tr>")
        builder.appendLine("  <td><a href=\"$path\">$name</a></td>")
        builder.appendLine("  <td>${FileSizeFormatter.format(size)}</td>")

        if (modified != null) {
            builder.appendLine("  <td>${dateFormat.format(modified)}</td>")
        } else {
            builder.appendLine("  <td>-</td>")
        }

        builder.appendLine("</tr>")
    }

    /**
     * Encodes the path like `HttpUtility.UrlPathEncode` does: non-ASCII and control characters (UTF-8, lower
     * case hex digits) and spaces are percent-encoded, everything else (including the query string) is kept.
     */
    private fun urlPathEncode(value: String): String {
        if (value.isEmpty()) {
            return value
        }

        val query = value.indexOf('?')

        if (query >= 0) {
            return urlPathEncode(value.substring(0, query)) + value.substring(query)
        }

        val result = StringBuilder(value.length + 8)

        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF

            if (b >= 0x7F || b < 0x20) {
                result.append('%').append(HEX[b shr 4]).append(HEX[b and 0xF])
            } else if (b == ' '.code) {
                result.append("%20")
            } else {
                result.append(b.toChar())
            }
        }

        return result.toString()
    }

    private const val HEX = "0123456789abcdef"

}
