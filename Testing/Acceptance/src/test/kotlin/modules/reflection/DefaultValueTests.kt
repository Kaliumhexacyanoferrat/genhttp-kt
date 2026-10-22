package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.FromBody
import org.genhttp.modules.reflection.Method
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.math.BigDecimal
import java.net.http.HttpRequest.BodyPublishers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import kotlin.test.assertEquals

class DefaultValueTests {

    // region Supporting data structures

    enum class SortOrder {
        Ascending,
        Descending
    }

    class DefaultResource {

        @ResourceMethod(path = "int")
        fun getInt(limit: Int = 50): Int = limit

        @ResourceMethod(path = "bool")
        fun getBool(descending: Boolean = true): Boolean = descending

        @ResourceMethod(path = "string")
        fun getString(name: String = "fallback"): String = name

        @ResourceMethod(path = "enum")
        fun getEnum(order: SortOrder = SortOrder.Descending): SortOrder = order

        @ResourceMethod(path = "double")
        fun getDouble(factor: Double = 1.5): Double = factor

        @ResourceMethod(path = "decimal")
        fun getDecimal(amount: BigDecimal = BigDecimal("2.5")): BigDecimal = amount

        @ResourceMethod(path = "long")
        fun getLong(big: Long = 5_000_000_000L): Long = big

        @ResourceMethod(path = "nullable")
        fun getNullable(limit: Int? = 7): String = limit?.toString() ?: "null"

        @ResourceMethod(path = "nan")
        fun getNaN(value: Double = Double.NaN): Double = value

        @ResourceMethod(path = "date")
        fun getDate(date: Instant = Instant.parse("2000-01-01T00:00:00Z")): String = LocalDate.ofInstant(date, ZoneOffset.UTC).toString()

        @ResourceMethod(path = "short")
        fun getShort(value: Short = -3): Short = value

        @ResourceMethod(path = "nullable-enum")
        fun getNullableEnum(order: SortOrder? = SortOrder.Descending): String = order?.toString() ?: "null"

        @ResourceMethod(Method.Post, "body")
        fun postBody(@FromBody value: Int = 42): Int = value

        // .NET renders booleans as "True" / "False"
        @ResourceMethod(path = "mixed")
        fun getMixed(offset: Int, limit: Int = 50, descending: Boolean = true): String =
            "$offset|$limit|${descending.toString().replaceFirstChar { it.uppercaseChar() }}"

    }

    // endregion

    // region Handlers

    fun inlineLimit(limit: Int = 50): Int = limit

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testIntDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/int", "50")
    }

    @MultiEngineFrameworkTest
    fun testIntDefaultOverridden(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/int?limit=10", "10")
    }

    @MultiEngineFrameworkTest
    fun testIntDefaultWithEmptyValue(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/int?limit=", "50")
    }

    @MultiEngineFrameworkTest
    fun testBoolDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/bool", "1")
    }

    @MultiEngineFrameworkTest
    fun testBoolDefaultOverridden(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/bool?descending=false", "0")
    }

    @MultiEngineFrameworkTest
    fun testStringDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/string", "fallback")
    }

    @MultiEngineFrameworkTest
    fun testEnumDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/enum", "Descending")
    }

    @MultiEngineFrameworkTest
    fun testDoubleDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/double", "1.5")
    }

    @MultiEngineFrameworkTest
    fun testDecimalDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/decimal", "2.5")
    }

    @MultiEngineFrameworkTest
    fun testLongDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/long", "5000000000")
    }

    @MultiEngineFrameworkTest
    fun testNullableDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/nullable", "7")
    }

    @MultiEngineFrameworkTest
    fun testNaNDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/nan", "NaN")
    }

    @MultiEngineFrameworkTest
    fun testNonLiteralDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/date", "2000-01-01")
    }

    @MultiEngineFrameworkTest
    fun testShortDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/short", "-3")
    }

    @MultiEngineFrameworkTest
    fun testNullableEnumDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/nullable-enum", "Descending")
    }

    @MultiEngineFrameworkTest
    fun testBodyDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Layout.create()
            .addService<DefaultResource>("t", mode = mode)

        TestHost.run(app, engine = engine).use { runner ->
            val request = runner.getRequest("/t/body")
                .method("POST", BodyPublishers.ofString(""))
                .header("Content-Type", "text/plain")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testMixedDefaults(engine: ServerEngine, mode: ExecutionMode) = test {
        assertResult(engine, mode, "/t/mixed?offset=3", "3|50|True")
    }

    @MultiEngineFrameworkTest
    fun testInlineDefault(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .get(this@DefaultValueTests::inlineLimit)
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val response = runner.getResponse("/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("50", response.getContent())
        }
    }

    private suspend fun assertResult(engine: ServerEngine, mode: ExecutionMode, path: String, expected: String) {
        val app = Layout.create()
            .addService<DefaultResource>("t", mode = mode)

        TestHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse(path)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals(expected, response.getContent())
        }
    }

    // endregion

}
