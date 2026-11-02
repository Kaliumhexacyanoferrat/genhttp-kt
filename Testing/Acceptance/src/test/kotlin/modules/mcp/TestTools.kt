package org.genhttp.testing.acceptance.modules.mcp

import com.fasterxml.jackson.annotation.JsonPropertyDescription

import kotlinx.coroutines.yield

import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.documentation.Summary
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.mcp.AudioContent
import org.genhttp.modules.mcp.ImageContent
import org.genhttp.modules.mcp.ResourceLinkContent
import org.genhttp.modules.mcp.TextContent
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.ToolResult
import org.genhttp.modules.mcp.Tools
import org.genhttp.modules.mcp.provider.ToolsHandlerBuilder
import org.genhttp.testing.TestHost

import java.nio.ByteBuffer

/**
 * The unit of a temperature (`Unit` upstream, renamed as it would shadow `kotlin.Unit` in this package).
 */
enum class TemperatureUnit { Celsius, Fahrenheit }

data class Alert(val kind: String, val note: String?)

data class Forecast(
    val city: String,
    val temperature: Double,
    val warning: String?,
    val conditions: List<String>,
    val unit: TemperatureUnit?,
    val alerts: List<Alert>?
)

class TestTools {

    @Tool
    @Summary("Adds two integers")
    fun add(@JsonPropertyDescription("The first summand") a: Int, @Summary("The second summand") b: Int): Int = a + b

    @Tool(title = "Weather forecast", readOnly = [true], destructive = [false], idempotent = [true], openWorld = [true])
    @Summary("Returns the weather forecast for a city")
    suspend fun getForecast(@Summary("The city to forecast") city: String, unit: TemperatureUnit = TemperatureUnit.Celsius, days: Int? = null): Forecast {
        yield()

        return Forecast(city, if (unit == TemperatureUnit.Celsius) 21.5 else 70.7, null, listOf("sunny", "${days ?: 1} day(s)"), unit, listOf(Alert("wind", null)))
    }

    @Tool
    @Summary("Returns the value of the X-Test header")
    fun readHeader(request: Request): String = request.header.headers.getEntry("X-Test") ?: ""

    @Tool
    @Summary("Always fails")
    fun fail(): String = throw ProviderException(ResponseStatus.BadRequest, "The date needs to be in the future")

    @Tool
    @Summary("Always crashes")
    suspend fun crash(): String = throw IllegalStateException("Database is down")

    @Tool
    @Summary("Crashes after doing some work")
    suspend fun crashLater(): String {
        yield()
        throw IllegalStateException("Disk is full")
    }

    @Tool
    @Summary("Returns Markdown")
    fun markdown(): ToolResult = ToolResult.text("# Title")

    @Tool
    @Summary("Returns some content blocks")
    fun image(): ToolResult = ToolResult.from(
        TextContent("A pixel"),
        ImageContent(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), "image/png"),
        AudioContent(ByteBuffer.wrap(byteArrayOf(4, 5)), "audio/wav"),
        ResourceLinkContent("https://example.com/a.txt", "a.txt", title = "A", description = "A file", mimeType = "text/plain")
    )

    @Tool
    @Summary("Returns structured data")
    fun structured(): ToolResult = ToolResult.json(mapOf("ok" to true, "count" to 2))

    @Tool
    @Summary("Returns a list")
    fun list(): List<String> = listOf("a", "b")

    @Tool
    @Summary("Echoes the text")
    fun echo(text: String): String = text

    @Tool
    @Summary("Returns an error result")
    fun errorResult(): ToolResult = ToolResult.error("Not today")

}

internal object TestServer {

    const val ToolCount = 12

    fun create(): ToolsHandlerBuilder = Tools.create()
        .server("test-server", "1.0.0", "Test Server")
        .instructions("Call add to add numbers.")
        .add<TestTools>()

    suspend fun run(engine: ServerEngine = ServerEngine.Internal): TestHost = TestHost.run(create(), engine = engine)

}
