package org.genhttp.testing.acceptance.modules.mcp

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

import org.genhttp.api.content.documentation.Summary
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.Tools
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

import java.net.URI
import java.time.LocalDateTime

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationTests {

    // region Supporting data structures

    data class SkillLevel(val name: String?, val skillId: String?, @field:Min(1) @field:Max(5) val level: Int)

    data class Ranked(@Min(1) @Max(3) val rank: Int)

    data class TreeNode(val name: String, @field:Size(max = 2) val children: List<TreeNode>?)

    /**
     * The values allowed for `choice` (`[AllowedValues("a", "b")]` upstream, which has no counterpart in Bean Validation).
     */
    enum class Choice { a, b }

    class SkillTools {

        @Tool
        @Summary("Adds or changes skills")
        fun upsertSkills(@Size(min = 1, max = 3) skills: List<SkillLevel>): Int = skills.size

        @Tool
        @Summary("Uses all kinds of constraints")
        fun constrained(
            @Min(1) @Max(100) limit: Int,
            @Size(min = 2, max = 10) code: String,
            @Pattern(regexp = "^[a-z]+$") slug: String,
            @Email mail: String,
            link: URI?,
            choice: Choice,
            @DecimalMin("0.5") @DecimalMax("1.5") factor: Double,
            @DecimalMin("1", inclusive = false) @DecimalMax("10", inclusive = false) exclusive: Int,
            @Size(min = 1) tags: Array<String>,
            @Size(max = 2) few: Array<String>,
            @Past `when`: LocalDateTime,
            @NotNull must: String? = null
        ): String = "ok"

        @Tool
        @Summary("Validates a parameter-level attribute of a record")
        fun rank(ranked: Ranked): Int = ranked.rank

        @Tool
        @Summary("Validates a recursive structure")
        fun tree(root: TreeNode): String = root.name

    }

    // endregion

    // region Helpers

    private suspend fun run(): TestHost = TestHost.run(Tools.create().add<SkillTools>())

    private suspend fun getSchema(host: TestHost, tool: String): JsonNode {
        val response = McpClient.modern(host).request("tools/list")

        return response.result["tools"]!!.single { it["name"]!!.stringValue() == tool }["inputSchema"]!!
    }

    private fun validArguments(): ObjectNode = jsonObject(
        "limit" to 10,
        "code" to "abc",
        "slug" to "slug",
        "mail" to "a@b.ch",
        "choice" to "a",
        "factor" to 1.0,
        "exclusive" to 5,
        "tags" to jsonArray("x"),
        "few" to jsonArray("x"),
        "when" to "2025-01-01T00:00:00",
        "must" to "set"
    )

    // endregion

    // region Schema

    @Test
    fun testNestedConstraintsAndNullables() = test {
        run().use { host ->
            val skills = getSchema(host, "upsert_skills")["properties"]!!["skills"]!!

            assertEquals(1, skills["minItems"]!!.intValue())
            assertEquals(3, skills["maxItems"]!!.intValue())

            val item = skills["items"]!!

            assertEquals("""["level"]""", item["required"]!!.toString())
            assertEquals(1, item["properties"]!!["level"]!!["minimum"]!!.intValue())
            assertEquals(5, item["properties"]!!["level"]!!["maximum"]!!.intValue())
        }
    }

    @Test
    fun testParameterConstraints() = test {
        run().use { host ->
            val properties = getSchema(host, "constrained")["properties"]!!

            assertEquals(1, properties["limit"]!!["minimum"]!!.intValue())
            assertEquals(100, properties["limit"]!!["maximum"]!!.intValue())

            assertEquals(2, properties["code"]!!["minLength"]!!.intValue())
            assertEquals(10, properties["code"]!!["maxLength"]!!.intValue())

            assertEquals("^[a-z]+$", properties["slug"]!!["pattern"]!!.stringValue())
            assertEquals("email", properties["mail"]!!["format"]!!.stringValue())
            assertEquals("uri", properties["link"]!!["format"]!!.stringValue())
            assertEquals("""["a","b"]""", properties["choice"]!!["enum"]!!.toString())

            assertEquals(0.5, properties["factor"]!!["minimum"]!!.doubleValue())
            assertEquals(1.5, properties["factor"]!!["maximum"]!!.doubleValue())

            assertEquals(1, properties["exclusive"]!!["exclusiveMinimum"]!!.intValue())
            assertEquals(10, properties["exclusive"]!!["exclusiveMaximum"]!!.intValue())

            assertEquals(1, properties["tags"]!!["minItems"]!!.intValue())
            assertEquals(2, properties["few"]!!["maxItems"]!!.intValue())

            assertNull(properties["when"]!!["minimum"])
        }
    }

    @Test
    fun testParameterLevelRecordConstraints() = test {
        run().use { host ->
            val rank = getSchema(host, "rank")["properties"]!!["ranked"]!!["properties"]!!["rank"]!!

            assertEquals(3, rank["maximum"]!!.intValue())
        }
    }

    // endregion

    // region Validation

    @Test
    fun testOptionalNullables() = test {
        run().use { host ->
            val response = McpClient.modern(host).call(
                "upsert_skills", jsonObject(
                    "skills" to jsonArray(jsonObject("name" to "Kotlin", "level" to 4))
                )
            )

            assertFalse(response.isError, response.text)
            assertEquals("1", response.text)
        }
    }

    @Test
    fun testNonNullablesStayRequired() = test {
        run().use { host ->
            val response = McpClient.modern(host).call(
                "upsert_skills", jsonObject(
                    "skills" to jsonArray(jsonObject("name" to "Kotlin"))
                )
            )

            assertTrue(response.isError)
            AssertX.contains("level", response.text)
        }
    }

    @Test
    fun testAllViolationsAreReported() = test {
        run().use { host ->
            val response = McpClient.modern(host).call(
                "upsert_skills", jsonObject(
                    "skills" to jsonArray(
                        jsonObject("name" to "A", "level" to 9),
                        jsonObject("name" to "B", "level" to 1),
                        jsonObject("name" to "C", "level" to 0),
                        jsonObject("name" to "D", "level" to 2)
                    )
                )
            )

            assertTrue(response.isError)

            // the default messages of Bean Validation instead of the ones of .NET's DataAnnotations (docs/DEVIATIONS.md)
            assertEquals(
                "The arguments are invalid:\n" +
                    "- skills: size must be between 1 and 3\n" +
                    "- skills[0].level: must be less than or equal to 5\n" +
                    "- skills[2].level: must be greater than or equal to 1", response.text
            )
        }
    }

    @Test
    fun testValidArguments() = test {
        run().use { host ->
            val response = McpClient.modern(host).call("constrained", validArguments())

            assertFalse(response.isError, response.text)
        }
    }

    @ParameterizedTest
    @MethodSource("violations")
    fun testViolations(argument: String, value: String) = test {
        run().use { host ->
            val arguments = validArguments()

            arguments.set(argument, parseJson(value))

            val response = McpClient.modern(host).call("constrained", arguments)

            assertTrue(response.isError, "Expected '$argument' = $value to be rejected")
            AssertX.contains("- $argument: ", response.text)
        }
    }

    @Test
    fun testParameterLevelRecordValidation() = test {
        run().use { host ->
            val response = McpClient.modern(host).call("rank", jsonObject("ranked" to jsonObject("rank" to 7)))

            assertTrue(response.isError)
            AssertX.contains("ranked.rank", response.text)
        }
    }

    @Test
    fun testRecursiveValidation() = test {
        run().use { host ->
            val tooMany = jsonArray(jsonObject("name" to "a"), jsonObject("name" to "b"), jsonObject("name" to "c"))

            val response = McpClient.modern(host).call(
                "tree", jsonObject(
                    "root" to jsonObject("name" to "root", "children" to jsonArray(jsonObject("name" to "child", "children" to tooMany)))
                )
            )

            assertTrue(response.isError)
            AssertX.contains("root.children[0].children", response.text)

            val valid = McpClient.modern(host).call("tree", jsonObject("root" to jsonObject("name" to "root")))

            assertEquals("root", valid.text)
        }
    }

    // endregion

    companion object {

        @JvmStatic
        fun violations(): List<Arguments> = listOf(
            Arguments.of("limit", "0"),
            Arguments.of("code", "\"a\""),
            Arguments.of("slug", "\"UPPER\""),
            Arguments.of("mail", "\"no-mail\""),
            Arguments.of("link", "\"no link\""),
            Arguments.of("choice", "\"c\""),
            Arguments.of("factor", "2.0"),
            Arguments.of("exclusive", "1"),
            Arguments.of("tags", "[]"),
            Arguments.of("few", "[\"a\",\"b\",\"c\"]"),
            Arguments.of("when", "\"2040-01-01T00:00:00\""),
            Arguments.of("must", "null")
        )

    }

}
