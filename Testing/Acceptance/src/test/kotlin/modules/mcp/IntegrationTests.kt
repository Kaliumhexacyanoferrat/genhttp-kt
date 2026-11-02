package org.genhttp.testing.acceptance.modules.mcp

import com.fasterxml.jackson.annotation.JsonPropertyDescription

import kotlinx.coroutines.yield

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.documentation.ApiResponse
import org.genhttp.api.content.documentation.Remarks
import org.genhttp.api.content.documentation.Summary
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.RequireRole
import org.genhttp.modules.authentication.setUser
import org.genhttp.modules.io.from
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.Tools
import org.genhttp.modules.mcp.add
import org.genhttp.modules.mcp.provider.ToolsHandlerBuilder
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.InterceptWith
import org.genhttp.modules.reflection.InterceptionResult
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.Result
import org.genhttp.modules.reflection.injectors.UserInjector
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

import java.io.InputStream
import java.net.http.HttpRequest
import java.util.concurrent.atomic.AtomicInteger

import kotlin.reflect.KClass
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntegrationTests {

    // region Supporting data structures

    enum class Genre { Fiction, Science }

    data class Book(val id: Int, val title: String, val genre: Genre)

    data class Receipt(val bookId: Int, val note: String?)

    class TestUser(name: String, vararg roles: String) : User {

        override val displayName: String = name

        override val roles: List<String> = roles.toList()

    }

    class ShortCircuit : OperationInterceptor {

        override fun configure(attribute: Annotation) {}

        override suspend fun intercept(request: Request, operation: Operation, arguments: Map<ByteString, Any?>): InterceptionResult? {
            val value = arguments[ByteString("value")] as Int

            return if (value == 0) InterceptionResult("short circuit") else null
        }

    }

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    @InterceptWith(ShortCircuit::class)
    annotation class InterceptWithShortCircuit

    class LibraryTools {

        @Tool(title = "Find books", readOnly = [true])
        @Summary("Searches the library")
        suspend fun findBooksAsync(@JsonPropertyDescription("Part of the title") query: String, request: Request, limit: Int = 10, genre: Genre? = null): List<Book> {
            yield()

            val source = request.header.headers.getEntry("X-Source") ?: "default"

            return listOf(Book(limit, "$query ($source)", genre ?: Genre.Fiction))
        }

        @Tool("lend", destructive = [false])
        @JsonPropertyDescription("Lends a book")
        suspend fun lend(bookId: Int): Result<Receipt> = Result(Receipt(bookId, null))

        @Tool
        @Summary("Returns a book")
        suspend fun returnBook(bookId: Int) {}

        @Tool
        @Summary("Touches a book")
        suspend fun touch() {}

        @Tool
        @Summary("Resets the library")
        fun reset() {}

        @Tool
        @Summary("Fails")
        @Remarks("Fails on purpose.")
        @ApiResponse(ResponseStatus.Ok, "Never returned")
        @ApiResponse(ResponseStatus.Conflict, "The book is already lent")
        @ApiResponse(ResponseStatus.Gone)
        fun fail(): String = throw ProviderException(ResponseStatus.Conflict, "The book is already lent")

        @Tool
        @Summary("Crashes")
        fun crash(): String = throw IllegalStateException("Database is down")

        @Tool
        @Summary("Crashes asynchronously")
        suspend fun crashAsyncLater(): String {
            yield()
            throw IllegalStateException("Later")
        }

        @Tool
        @Summary("Removes all books")
        @RequireRole("admin")
        fun purge(): String = "purged"

        @Tool
        @Summary("Tells who is calling")
        fun whoAmI(user: TestUser): String = user.displayName

        @Tool
        @Summary("Might be intercepted")
        @InterceptWithShortCircuit
        fun intercepted(value: Int): String = "value $value"

        fun notATool(): String = "nope"

        companion object {

            @Tool
            @Summary("Returns the HTTP status")
            @JvmStatic
            fun getHTTPStatus(): Int = 200

        }

    }

    class MixedService {

        @ResourceMethod
        fun get(): String = "service"

        @Tool
        @Summary("A tool")
        fun tool(): String = "tool"

    }

    class EmptyTools {

        fun nothing(): String = ""

    }

    class UndescribedTools {

        @Tool
        fun undescribed(): String = ""

    }

    class InvalidNameTools {

        @Tool("in valid")
        @Summary("Invalid")
        fun invalid(): String = ""

    }

    class HandlerTools {

        @Tool
        @Summary("Returns a handler")
        fun handler(): Handler = throw UnsupportedOperationException()

    }

    class StreamTools {

        @Tool
        @Summary("Takes a stream")
        fun upload(data: InputStream): String = ""

    }

    // endregion

    // region Helpers

    private fun library(): ToolsHandlerBuilder = Tools.create()
        .add<LibraryTools>()
        .injectors(Injection.default().add(UserInjector(TestUser::class)))
        .add(Concern.from { request, content ->
            val name = request.header.headers.getEntry("X-User")

            if (name != null) {
                val roles = request.header.headers.getEntry("X-Roles")?.split(',')?.toTypedArray() ?: emptyArray()

                request.setUser(TestUser(name, *roles))
            }

            content.handle(request)
        })

    private suspend fun getTool(host: TestHost, name: String): JsonNode {
        val response = McpClient.modern(host).request("tools/list")

        return response.result["tools"]!!.single { it["name"]!!.stringValue() == name }
    }

    private fun asUser(user: String, roles: String = ""): (HttpRequest.Builder) -> Unit = { r ->
        r.header("X-User", user)
        r.header("X-Roles", roles)
    }

    // endregion

    // region Definitions

    @Test
    fun testNamesAreDerived() = test {
        TestHost.run(library()).use { host ->
            val tools = McpClient.modern(host).request("tools/list")

            val names = tools.result["tools"]!!.values().map { it["name"]!!.stringValue() }.toHashSet()

            assertContains(names, "find_books")
            assertContains(names, "lend")
            assertContains(names, "return_book")
            assertContains(names, "crash_async_later")
            assertContains(names, "who_am_i")
            assertContains(names, "get_http_status")
            assertFalse(names.contains("not_a_tool"))
        }
    }

    @Test
    fun testArgumentSchema() = test {
        TestHost.run(library()).use { host ->
            val tool = getTool(host, "find_books")

            assertEquals("Searches the library", tool["description"]!!.stringValue())
            assertEquals("Find books", tool["title"]!!.stringValue())

            val schema = tool["inputSchema"]!!

            assertEquals("""["query"]""", schema["required"]!!.toString())

            val properties = schema["properties"]!! as ObjectNode

            assertEquals(3, properties.size())
            assertFalse(properties.has("request"))

            assertEquals("Part of the title", properties["query"]!!["description"]!!.stringValue())

            // upstream: 10 - kotlin-reflect cannot read default values, so no default keyword is generated (docs/DEVIATIONS.md)
            assertNull(properties["limit"]!!["default"])

            assertNotNull(properties["genre"]!!["enum"])
        }
    }

    @Test
    fun testInterceptorDocumentation() = test {
        TestHost.run(library()).use { host ->
            val description = getTool(host, "purge")["description"]!!.stringValue()

            assertEquals("Removes all books\n\nPossible errors:\n- Authentication is required to access this operation\n- The user does not have the required role: admin", description)
        }
    }

    @Test
    fun testDeclaredDocumentation() = test {
        TestHost.run(library()).use { host ->
            val description = getTool(host, "fail")["description"]!!.stringValue()

            assertEquals("Fails\n\nFails on purpose.", description)

            assertEquals("Resets the library", getTool(host, "reset")["description"]!!.stringValue())
        }
    }

    @Test
    fun testAnnotations() = test {
        TestHost.run(library()).use { host ->
            val find = getTool(host, "find_books")

            assertTrue(find["annotations"]!!["readOnlyHint"]!!.booleanValue())
            assertNull(find["annotations"]!!["destructiveHint"])

            val lend = getTool(host, "lend")

            assertFalse(lend["annotations"]!!["destructiveHint"]!!.booleanValue())
            assertNull(lend["annotations"]!!["readOnlyHint"])

            assertNull(getTool(host, "reset")["annotations"])
        }
    }

    @Test
    fun testOutputSchemas() = test {
        TestHost.run(library()).use { host ->
            val lend = getTool(host, "lend")

            assertEquals("""["bookId"]""", lend["outputSchema"]!!["required"]!!.toString())

            assertNull(getTool(host, "find_books")["outputSchema"])
            assertNull(getTool(host, "reset")["outputSchema"])
        }
    }

    @Test
    fun testServicesAreRejected() {
        val e = assertFailsWith<IllegalStateException> { Tools.create().add<MixedService>() }

        AssertX.contains("dedicated class", e.message)
    }

    @Test
    fun testEmptyClassIsRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add<EmptyTools>() }
    }

    @Test
    fun testDescriptionIsRequired() {
        assertFailsWith<IllegalStateException> { Tools.create().add<UndescribedTools>() }
    }

    @Test
    fun testInvalidNameIsRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add<InvalidNameTools>() }
        assertFailsWith<IllegalStateException> { Tools.create().add("in valid", "Invalid") { 1 } }
    }

    @Test
    fun testDuplicatesAreRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add("lend", "Lends") { 1 }.add<LibraryTools>().build() }
        assertFailsWith<IllegalStateException> { Tools.create().add("a", "A") { 1 }.add("a", "A") { 2 }.build() }
    }

    @Test
    fun testUnsupportedResultIsRejected() = test {
        assertPreparationFails(Tools.create().add<HandlerTools>(), "cannot be sent to a client")
    }

    @Test
    fun testStreamArgumentIsRejected() = test {
        assertPreparationFails(Tools.create().add<StreamTools>(), "stream")
    }

    @Test
    fun testPreparationIsIdempotent() = test {
        val handler = Tools.create().add<LibraryTools>().build()

        TestHost.run(Tools.create().add<TestTools>()).use { host ->
            handler.prepare(host.host.instance!!)
            handler.prepare(host.host.instance!!)
        }
    }

    private suspend fun assertPreparationFails(builder: ToolsHandlerBuilder, message: String) {
        val handler = builder.build()

        TestHost.run(Tools.create().add<TestTools>()).use { host ->
            val e = assertFailsWith<IllegalStateException> { handler.prepare(host.host.instance!!) }

            AssertX.contains(message, e.message)
        }
    }

    // endregion

    // region Invocation

    @MultiEngineTest
    fun testInvocation(engine: ServerEngine) = test {
        TestHost.run(library(), engine = engine).use { host ->
            val response = McpClient.modern(host).call("find_books", jsonObject("query" to "Dune", "genre" to "Science")) { r -> r.header("X-Source", "test") }

            val book = response.result["structuredContent"]!![0]!!

            assertEquals("Dune (test)", book["title"]!!.stringValue())
            assertEquals(10, book["id"]!!.intValue())
            assertEquals("Science", book["genre"]!!.stringValue())
        }
    }

    @Test
    fun testResultIsUnwrapped() = test {
        TestHost.run(library()).use { host ->
            val response = McpClient.legacy(host).call("lend", jsonObject("bookId" to 7))

            assertEquals(7, response.result["structuredContent"]!!["bookId"]!!.intValue())
            assertEquals("""{"bookId":7}""", response.text)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "return_book, '{\"bookId\":1}'",
        "touch, '{}'",
        "reset, '{}'"
    )
    fun testVoidResults(tool: String, arguments: String) = test {
        TestHost.run(library()).use { host ->
            val response = McpClient.modern(host).call(tool, parseJson(arguments) as ObjectNode)

            assertFalse(response.isError)
            assertEquals(0, response.result["content"]!!.size())
        }
    }

    @Test
    fun testStaticMethod() = test {
        TestHost.run(library()).use { host ->
            val response = McpClient.modern(host).call("get_http_status")

            assertEquals("200", response.text)
        }
    }

    // endregion

    // region Injection and interception

    @Test
    fun testRoles() = test {
        TestHost.run(library()).use { host ->
            val client = McpClient.modern(host)

            val anonymous = client.call("purge")

            assertTrue(anonymous.isError)
            AssertX.contains("Authorization required", anonymous.text)

            val user = client.call("purge", configure = asUser("joe", "reader"))

            assertTrue(user.isError)
            AssertX.contains("not authorized", user.text)

            val admin = client.call("purge", configure = asUser("ann", "admin"))

            assertFalse(admin.isError)
            assertEquals("purged", admin.text)
        }
    }

    @Test
    fun testUserInjection() = test {
        TestHost.run(library()).use { host ->
            val client = McpClient.modern(host)

            assertEquals("ann", client.call("who_am_i", configure = asUser("ann")).text)

            val anonymous = client.call("who_am_i")

            assertTrue(anonymous.isError)
            AssertX.contains("Authentication required", anonymous.text)
        }
    }

    @Test
    fun testInterception() = test {
        TestHost.run(library()).use { host ->
            val client = McpClient.modern(host)

            assertEquals("short circuit", client.call("intercepted", jsonObject("value" to 0)).text)
            assertEquals("value 1", client.call("intercepted", jsonObject("value" to 1)).text)
        }
    }

    // endregion

    // region Function tools (annotations cannot be placed on lambdas, so they are declared as functions)

    @Tool(title = "Multiplication", readOnly = [true])
    fun multiply(@JsonPropertyDescription("The first factor") a: Int, b: Int): Int = a * b

    @RequireRole("admin")
    fun secret(): String = "42"

    // endregion

    // region Functions

    @MultiEngineTest
    fun testFunction(engine: ServerEngine) = test {
        val tools = Tools.create()
            .add("multiply", "Multiplies two numbers", this@IntegrationTests::multiply)

        TestHost.run(tools, engine = engine).use { host ->
            val tool = getTool(host, "multiply")

            assertEquals("Multiplies two numbers", tool["description"]!!.stringValue())
            assertEquals("Multiplication", tool["title"]!!.stringValue())
            assertTrue(tool["annotations"]!!["readOnlyHint"]!!.booleanValue())
            assertEquals("The first factor", tool["inputSchema"]!!["properties"]!!["a"]!!["description"]!!.stringValue())

            val response = McpClient.modern(host).call("multiply", jsonObject("a" to 6, "b" to 7))

            assertEquals("42", response.text)
        }
    }

    @Test
    fun testFunctionWithInterceptor() = test {
        val tools = Tools.create()
            .add("secret", "Reveals the secret", this@IntegrationTests::secret)
            .add(Concern.from { request, content ->
                request.setUser(TestUser("joe", "reader"))
                content.handle(request)
            })

        TestHost.run(tools).use { host ->
            val response = McpClient.modern(host).call("secret")

            assertTrue(response.isError)
        }
    }

    @Test
    fun testAsyncFunctionWithRequest() = test {
        val tools = Tools.create()
            .add("echo", "Echoes a header") { request: Request ->
                yield()
                request.header.headers.getEntry("X-Echo")
            }

        TestHost.run(tools).use { host ->
            val response = McpClient.modern(host).call("echo", configure = { r -> r.header("X-Echo", "hello") })

            assertEquals("hello", response.text)
            assertFalse((getTool(host, "echo")["inputSchema"]!!["properties"]!! as ObjectNode).has("request"))
        }
    }

    @Test
    fun testSingleDefaultInstance() = test {
        CountingTools.created.set(0)

        TestHost.run(Tools.create().add<CountingTools>()).use { host ->
            val client = McpClient.modern(host)

            assertEquals("1", client.call("instances").text)
            assertEquals("1", client.call("instances").text)
        }
    }

    @Test
    fun testCustomInstanceProvider() = test {
        val created = mutableListOf<KClass<*>>()

        val tools = Tools.create()
            .instanceProvider { type, request ->
                created.add(type)
                ConstructedTools(request.header.headers.getEntry("X-Prefix") ?: "none")
            }
            .add<ConstructedTools>()

        TestHost.run(tools).use { host ->
            val response = McpClient.modern(host).call("prefixed", jsonObject("value" to "x")) { r -> r.header("X-Prefix", "p") }

            assertEquals("p:x", response.text)
            assertEquals(1, created.size)
        }
    }

    @Test
    fun testConstructorIsRequired() {
        val e = assertFailsWith<IllegalStateException> { Tools.create().add<ConstructedTools>().build() }

        AssertX.contains("parameterless constructor", e.message)
    }

    class CountingTools {

        init {
            created.incrementAndGet()
        }

        @Tool
        @Summary("Returns how many instances were created")
        fun instances(): Int = created.get()

        companion object {

            internal val created = AtomicInteger()

        }

    }

    class ConstructedTools(private val prefix: String) {

        @Tool
        @Summary("Prefixes a value")
        fun prefixed(value: String): String = "$prefix:$value"

    }

    @Test
    fun testMixedRegistrations() = test {
        val tools = Tools.create()
            .add<LibraryTools>()
            .add(ValidationTests.SkillTools())
            .add("add", "Adds two numbers") { a: Int, b: Int -> a + b }
            .add("hello", "Says hello") { "hello" }

        TestHost.run(tools).use { host ->
            val client = McpClient.modern(host)

            assertEquals("3", client.call("add", jsonObject("a" to 1, "b" to 2)).text)
            assertEquals("200", client.call("get_http_status").text)
            assertEquals("hello", client.call("hello").text)
        }
    }

    // endregion

}
