package org.genhttp.testing.acceptance.modules.mcp

import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

import java.util.concurrent.ConcurrentLinkedQueue

import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoggingTests {

    @Test
    fun testOperationsAreLogged() = test {
        val provider = CollectingProvider()

        TestHost(TestServer.create().build()).use { host ->
            host.host.logging(provider, logRequests = false)

            host.start()

            McpClient.legacy(host, null).request(
                "initialize", jsonObject(
                    "protocolVersion" to "2025-06-18",
                    "clientInfo" to jsonObject("name" to "claude-code", "version" to "2.0.0")
                )
            )

            McpClient.legacy(host, null).notify("notifications/initialized")

            val client = McpClient.modern(host)

            client.request("server/discover")
            client.request("tools/list")
            client.call("add", jsonObject("a" to 1, "b" to 2))
            client.call("fail")
            client.call("crash")
            client.call("nope")
            client.call("error_result")

            val entries = provider.entries.filter { it.category == "org.genhttp.modules.mcp" }

            assertLogged(entries, Level.INFO, "MCP initialize from claude-code 2.0.0 answered in", "(protocol 2025-06-18)")
            assertLogged(entries, Level.DEBUG, "MCP notification notifications/initialized accepted")
            assertLogged(entries, Level.INFO, "MCP server/discover from test-client 1.2.3 answered in", "(protocol 2026-07-28)")
            assertLogged(entries, Level.INFO, "MCP tools/list answered in", "(protocol 2026-07-28)")
            assertLogged(entries, Level.INFO, "MCP tool add succeeded in")
            assertLogged(entries, Level.INFO, "MCP tool fail returned an error in", "The date needs to be in the future")
            assertLogged(entries, Level.ERROR, "MCP tool crash failed unexpectedly after")
            assertLogged(entries, Level.INFO, "MCP tool error_result returned an error in", "Not today")
            assertLogged(entries, Level.INFO, "MCP request rejected with HTTP 200", "Unknown tool: nope")

            assertNotNull(entries.single { it.level == Level.ERROR }.exception)
        }
    }

    private fun assertLogged(entries: List<LogEntry>, level: Level, vararg fragments: String) {
        assertTrue(
            entries.any { e -> e.level == level && fragments.all { e.message.contains(it) } },
            "Expected a $level entry with '${fragments.joinToString("', '")}', got:\n${entries.joinToString("\n") { "${it.level}: ${it.message}" }}"
        )
    }

    private data class LogEntry(val category: String, val level: Level?, val message: String, val exception: Throwable?)

    private class CollectingProvider : ILoggerFactory {

        val entries = ConcurrentLinkedQueue<LogEntry>()

        override fun getLogger(name: String): Logger = CollectingLogger(name, entries)

    }

    private class CollectingLogger(category: String, private val entries: ConcurrentLinkedQueue<LogEntry>) : LegacyAbstractLogger() {

        init {
            name = category
        }

        override fun isTraceEnabled(): Boolean = true

        override fun isDebugEnabled(): Boolean = true

        override fun isInfoEnabled(): Boolean = true

        override fun isWarnEnabled(): Boolean = true

        override fun isErrorEnabled(): Boolean = true

        override fun getFullyQualifiedCallerName(): String? = null

        override fun handleNormalizedLoggingCall(level: Level?, marker: Marker?, messagePattern: String?, arguments: Array<Any?>?, throwable: Throwable?) {
            entries.add(LogEntry(getName(), level, MessageFormatter.arrayFormat(messagePattern, arguments, throwable).message.orEmpty(), throwable))
        }

    }

}
