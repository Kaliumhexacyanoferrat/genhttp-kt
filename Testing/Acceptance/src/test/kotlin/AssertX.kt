package org.genhttp.testing.acceptance

import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.testing.getContent

import java.net.http.HttpResponse

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Additional assertions used by the acceptance tests.
 */
object AssertX {

    fun contains(searchFor: String, content: String?) {
        if (content == null || !content.contains(searchFor)) {
            fail("String '$searchFor' is not found in result:\r\n\r\n$content")
        }
    }

    fun doesNotContain(searchFor: String, content: String?) {
        if (content != null && content.contains(searchFor)) {
            fail("String '$searchFor' is found in result:\r\n\r\n$content")
        }
    }

    fun startsWith(searchFor: String, content: String?) = assertTrue(content?.startsWith(searchFor) ?: false)

    fun endsWith(searchFor: String, content: String?) = assertTrue(content?.endsWith(searchFor) ?: false)

    fun <T> single(collection: Iterable<T>) = assertEquals(1, collection.count())

    fun <T> empty(collection: Iterable<T>?) = assertFalse(collection?.any() ?: false)

    fun isNullOrEmpty(value: String?) = assertTrue(value.isNullOrEmpty())

}

/**
 * Asserts that the response has the given status, printing headers and body otherwise.
 */
fun HttpResponse<ByteArray>.assertStatus(expectedStatus: ResponseStatus) = assertStatus(expectedStatus.code)

/**
 * Asserts that the response has the given status code, printing headers and body otherwise.
 */
fun HttpResponse<ByteArray>.assertStatus(expectedStatus: Int) {
    if (statusCode() != expectedStatus) {
        val builder = StringBuilder()

        builder.appendLine("Response returned with status '${statusCode()}', expected '$expectedStatus'.")
        builder.appendLine()
        builder.appendLine("Headers")
        builder.appendLine()

        for ((key, values) in headers().map()) {
            builder.appendLine("  $key = ${values.joinToString(",")}")
        }

        builder.appendLine()

        val content = getContent()

        if (content.isNotEmpty()) {
            builder.appendLine("Body")
            builder.appendLine()
            builder.appendLine(content)
        }

        fail(builder.toString())
    }
}
