package org.genhttp.testing.acceptance.modules.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme as OpenApiSecurityScheme

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.documentation.ApiResponse
import org.genhttp.api.content.documentation.DocumentedConcern
import org.genhttp.api.content.documentation.OperationDocumentation
import org.genhttp.api.content.documentation.security.ApiKeyLocation
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.ApiKeyAuthentication
import org.genhttp.modules.authentication.BasicAuthentication
import org.genhttp.modules.authentication.BearerAuthentication
import org.genhttp.modules.authentication.ClientCertificateAuthentication
import org.genhttp.modules.authentication.MultiAuthentication
import org.genhttp.modules.authentication.RequireRole
import org.genhttp.modules.authentication.authentication
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.openapi.handler.OpenApiConcernBuilder
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.net.URI

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AuthenticationDocumentationTests {

    // region API keys

    @MultiEngineTest
    fun testApiKeyDefault(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .authentication(ApiKeyAuthentication.create().keys("123"))

        val doc = getDocument(api, engine)

        val scheme = doc.components?.securitySchemes?.getValue("apiKey")

        assertEquals(OpenApiSecurityScheme.Type.APIKEY, scheme?.type)
        assertEquals(OpenApiSecurityScheme.In.HEADER, scheme?.`in`)
        assertEquals("X-API-Key", scheme?.name)

        val op = getOperations(doc).single()

        assertEquals("apiKey", getSecurity(op.security))

        assertEquals(true, op.responses?.containsKey("401"))
        assertEquals(true, op.responses?.containsKey("403"))
        assertEquals(true, op.responses?.containsKey("200"))
    }

    @MultiEngineTest
    fun testApiKeyFromHeader(engine: ServerEngine) = test {
        val scheme = getScheme(ApiKeyAuthentication.create().withHeader("X-Token").keys("123"), engine)

        assertEquals(OpenApiSecurityScheme.In.HEADER, scheme.`in`)
        assertEquals("X-Token", scheme.name)
    }

    @MultiEngineTest
    fun testApiKeyFromQuery(engine: ServerEngine) = test {
        val scheme = getScheme(ApiKeyAuthentication.create().withQueryParameter("key").keys("123"), engine)

        assertEquals(OpenApiSecurityScheme.In.QUERY, scheme.`in`)
        assertEquals("key", scheme.name)
    }

    @MultiEngineTest
    fun testApiKeyFromCookie(engine: ServerEngine) = test {
        val scheme = getScheme(ApiKeyAuthentication.create().withCookie("session").keys("123"), engine)

        assertEquals(OpenApiSecurityScheme.In.COOKIE, scheme.`in`)
        assertEquals("session", scheme.name)
    }

    @MultiEngineTest
    fun testCustomExtractorIsNotDocumented(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .extractor { r -> r.header.headers.getEntry("X-Custom") }
            .keys("123")

        val doc = getDocument(Inline.create().get { 42 }.authentication(auth), engine)

        val op = getOperations(doc).single()

        assertNull(op.security)
        assertFalse((doc.components?.securitySchemes?.size ?: 0) > 0)

        assertEquals(true, op.responses?.containsKey("401"))
        assertEquals(true, op.responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testCustomExtractorWithScheme(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .extractor { r -> r.header.headers.getEntry("Authorization")?.replace("Token ", "") }
            .keys("123")
            .documentation(SecurityScheme.apiKey("Authorization", ApiKeyLocation.Header, "Send as 'Token <key>'"))

        val scheme = getScheme(auth, engine)

        assertEquals("Authorization", scheme.name)
        assertEquals("Send as 'Token <key>'", scheme.description)
    }

    @MultiEngineTest
    fun testSchemeWinsOverExtractorRegardlessOfOrder(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .documentation(SecurityScheme.apiKey("custom", ApiKeyLocation.Cookie))
            .withHeader("X-Token")
            .keys("123")

        val scheme = getScheme(auth, engine)

        assertEquals("custom", scheme.name)
        assertEquals(OpenApiSecurityScheme.In.COOKIE, scheme.`in`)
    }

    @MultiEngineTest
    fun testDescription(engine: ServerEngine) = test {
        val scheme = getScheme(ApiKeyAuthentication.create().keys("123").documentation("Request a key from support"), engine)

        assertEquals("X-API-Key", scheme.name)
        assertEquals("Request a key from support", scheme.description)
    }

    @MultiEngineTest
    fun testDescriptionOverridesScheme(engine: ServerEngine) = test {
        val first = ApiKeyAuthentication.create()
            .keys("123")
            .documentation("Overridden")
            .documentation(SecurityScheme.apiKey("X-Key", description = "Original"))

        val second = ApiKeyAuthentication.create()
            .keys("123")
            .documentation(SecurityScheme.apiKey("X-Key", description = "Original"))
            .documentation("Overridden")

        assertEquals("Overridden", getScheme(first, engine).description)
        assertEquals("Overridden", getScheme(second, engine).description)
    }

    @MultiEngineTest
    fun testRenamedScheme(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .keys("123")
            .documentation(SecurityScheme.apiKey("X-Partner-Key").copy(name = "partnerKey"))

        val doc = getDocument(Inline.create().get { 42 }.authentication(auth), engine)

        assertEquals("X-Partner-Key", doc.components?.securitySchemes?.getValue("partnerKey")?.name)
        assertEquals("partnerKey", getSecurity(getOperations(doc).single().security))
    }

    // endregion

    // region Basic

    @MultiEngineTest
    fun testBasic(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .authentication({ _: String, _: String -> null })

        val doc = getDocument(api, engine)

        val scheme = doc.components?.securitySchemes?.getValue("basic")

        assertEquals(OpenApiSecurityScheme.Type.HTTP, scheme?.type)
        assertEquals("basic", scheme?.scheme)

        val op = getOperations(doc).single()

        assertEquals("basic", getSecurity(op.security))
        assertEquals(true, op.responses?.getValue("401")?.headers?.containsKey("WWW-Authenticate"))
    }

    @MultiEngineTest
    fun testBasicWithKnownUsers(engine: ServerEngine) = test {
        val auth = BasicAuthentication.create()
            .add("bob", "secret")
            .documentation("Use your intranet credentials")

        val scheme = getScheme(auth, engine)

        assertEquals("basic", scheme.scheme)
        assertEquals("Use your intranet credentials", scheme.description)
    }

    @MultiEngineTest
    fun testBasicAsCustomHttpScheme(engine: ServerEngine) = test {
        val auth = BasicAuthentication.create({ _, _ -> null })
            .documentation(SecurityScheme.http("Digest"))

        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        assertEquals("Digest", doc.components?.securitySchemes?.getValue("digest")?.scheme)
    }

    // endregion

    // region Bearer

    @MultiEngineTest
    fun testBearer(engine: ServerEngine) = test {
        val doc = getDocument(Inline.create().get { 42 }.add(BearerAuthentication.create()), engine)

        val scheme = doc.components?.securitySchemes?.getValue("bearer")

        assertEquals(OpenApiSecurityScheme.Type.HTTP, scheme?.type)
        assertEquals("bearer", scheme?.scheme)
        assertEquals("JWT", scheme?.bearerFormat)

        val op = getOperations(doc).single()

        assertEquals("bearer", getSecurity(op.security))
        assertEquals(true, op.responses?.containsKey("400"))
        assertEquals(true, op.responses?.containsKey("401"))
        assertEquals(false, op.responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testBearerWithValidation(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .validation { _ -> }

        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        assertEquals(true, getOperations(doc).single().responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testBearerWithOpenIdConnect(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .documentation(SecurityScheme.openIdConnect("https://sso.example.com/.well-known/openid-configuration"))
            .documentation("Log in with your company account")

        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        val scheme = doc.components?.securitySchemes?.getValue("openIdConnect")

        assertEquals(OpenApiSecurityScheme.Type.OPENIDCONNECT, scheme?.type)
        assertEquals(URI("https://sso.example.com/.well-known/openid-configuration"), scheme?.openIdConnectUrl?.let { URI(it) })
        assertEquals("Log in with your company account", scheme?.description)

        assertEquals("openIdConnect", getSecurity(getOperations(doc).single().security))
    }

    // endregion

    // region Client certificates

    @MultiEngineTest
    fun testClientCertificate(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()

        val op = getOperations(getDocument(Inline.create().get { 42 }.add(auth), engine)).single()

        assertNull(op.security)
        assertEquals(false, op.responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testClientCertificateWithAuthorization(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()
            .authorization { _, _ -> true }
            .documentation(SecurityScheme.http("mutual", "Present a client certificate"))

        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        val op = getOperations(doc).single()

        assertEquals("mutual", getSecurity(op.security))
        assertEquals(true, op.responses?.containsKey("403"))
    }

    // endregion

    // region Multiple mechanisms

    @MultiEngineTest
    fun testMultiAuthenticationOffersAlternatives(engine: ServerEngine) = test {
        val auth = MultiAuthentication.create()
            .add(ApiKeyAuthentication.create().withHeader("X-Token").keys("123"))
            .add(BasicAuthentication.create().add("bob", "secret"))

        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        assertEquals(2, doc.components?.securitySchemes?.size)

        val op = getOperations(doc).single()

        assertEquals("apiKey | basic", getSecurity(op.security))

        assertEquals("The request could not be authenticated with any of the supported methods", op.responses?.getValue("401")?.description)
        assertEquals(true, op.responses?.getValue("401")?.headers?.containsKey("WWW-Authenticate"))
        assertEquals(true, op.responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testNestedConcernsAreAllRequired(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .authentication(ApiKeyAuthentication.create().keys("123"))
            .add(BearerAuthentication.create())

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("apiKey+bearer", getSecurity(op.security))
    }

    @MultiEngineTest
    fun testCustomConcernWithAlternativesAndRequirements(engine: ServerEngine) = test {
        val api = Inline.create()
            .get { 42 }
            .add(DocumentingConcernBuilder { d ->
                d.requireAuthentication(SecurityScheme.basic(), SecurityScheme.bearer())
                    .requireAuthentication(SecurityScheme.apiKey("X-Tenant"))
            })

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("apiKey+basic | apiKey+bearer", getSecurity(op.security))
    }

    @Test
    fun testRequirementNeedsScheme() {
        assertFailsWith<IllegalArgumentException> { OperationDocumentation(RequestMethod.Get).requireAuthentication() }
    }

    // endregion

    // region Scheme registration

    @MultiEngineTest
    fun testDifferentSchemesWithSameName(engine: ServerEngine) = test {
        val api = Layout.create()
            .add("a", Inline.create().get { 42 }.authentication(ApiKeyAuthentication.create().withHeader("X-A").keys("1")))
            .add("b", Inline.create().get { 42 }.authentication(ApiKeyAuthentication.create().withHeader("X-B").keys("1")))

        val doc = getDocument(api, engine)

        val schemes = doc.components?.securitySchemes

        assertEquals(2, schemes?.size)
        assertEquals("X-A", schemes?.getValue("apiKey")?.name)
        assertEquals("X-B", schemes?.getValue("apiKey2")?.name)

        assertEquals("apiKey", getSecurity(doc.paths.getValue("/a/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security))
        assertEquals("apiKey2", getSecurity(doc.paths.getValue("/b/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security))
    }

    @MultiEngineTest
    fun testEqualSchemesAreShared(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create().keys("1")

        val api = Layout.create()
            .add("a", Inline.create().get { 42 }.authentication(auth))
            .add("b", Inline.create().get { 42 }.authentication(auth))

        val doc = getDocument(api, engine)

        assertEquals(1, doc.components?.securitySchemes?.size)

        assertEquals("apiKey", getSecurity(doc.paths.getValue("/a/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security))
        assertEquals("apiKey", getSecurity(doc.paths.getValue("/b/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security))
    }

    @MultiEngineTest
    fun testUnsecuredContentHasNoSecurity(engine: ServerEngine) = test {
        val api = Layout.create()
            .add("secured", Inline.create().get { 42 }.authentication(ApiKeyAuthentication.create().keys("1")))
            .add("public", Inline.create().get { 42 })

        val doc = getDocument(api, engine)

        val op = doc.paths.getValue("/public/").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertNull(op?.security)
        assertEquals(false, op?.responses?.containsKey("401"))
    }

    // endregion

    // region Precedence

    @MultiEngineTest
    fun testOperationOverridesResponse(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@AuthenticationDocumentationTests::sendYourKey)
            .authentication(ApiKeyAuthentication.create().keys("1"))

        val op = getOperations(getDocument(api, engine)).single()

        assertEquals("Send your key", op.responses?.getValue("401")?.description)
    }

    // endregion

    // region Roles

    @MultiEngineTest
    fun testRequiredRole(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/admin", this@AuthenticationDocumentationTests::adminOnly)
            .get("/user") { 42 }

        val doc = getDocument(api, engine)

        val admin = doc.paths.getValue("/admin").readOperationsMap().getValue(PathItem.HttpMethod.GET)

        assertEquals(true, admin?.responses?.containsKey("401"))
        AssertX.contains("ADMIN", admin?.responses?.getValue("403")?.description)

        assertEquals(false, doc.paths.getValue("/user").readOperationsMap().getValue(PathItem.HttpMethod.GET)?.responses?.containsKey("403"))
    }

    @MultiEngineTest
    fun testRequiredRoles(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@AuthenticationDocumentationTests::adminOrAuditor)

        val op = getOperations(getDocument(api, engine)).single()

        AssertX.contains("ADMIN, AUDITOR", op.responses?.getValue("403")?.description)
    }

    @MultiEngineTest
    fun testRoleOverridesConcern(engine: ServerEngine) = test {
        val api = Inline.create()
            .get(this@AuthenticationDocumentationTests::adminOnly)
            .authentication(ApiKeyAuthentication.create().keys("1"))

        val op = getOperations(getDocument(api, engine)).single()

        AssertX.contains("ADMIN", op.responses?.getValue("403")?.description)
        assertEquals("apiKey", getSecurity(op.security))
    }

    // endregion

    // region Spec-wide security

    @MultiEngineTest
    fun testSpecWideSecurity(engine: ServerEngine) = test {
        val description = ApiDescription.create()
            .security(SecurityScheme.bearer(description = "Issued by the gateway"))

        val doc = getDocument(Inline.create().get { 42 }, engine, description)

        assertEquals("bearer", getSecurity(doc.security))
        assertEquals("Issued by the gateway", doc.components?.securitySchemes?.getValue("bearer")?.description)

        assertNull(getOperations(doc).single().security)
    }

    @MultiEngineTest
    fun testSpecWideSecurityIsCombinedWithConcerns(engine: ServerEngine) = test {
        val description = ApiDescription.create()
            .security(SecurityScheme.bearer())

        val api = Layout.create()
            .add("secured", Inline.create().get { 42 }.authentication(ApiKeyAuthentication.create().keys("1")))
            .add("public", Inline.create().get { 42 })

        val doc = getDocument(api, engine, description)

        assertEquals("bearer", getSecurity(doc.security))

        assertEquals("apiKey+bearer", getSecurity(doc.paths.getValue("/secured/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security))
        assertNull(doc.paths.getValue("/public/").readOperationsMap().getValue(PathItem.HttpMethod.GET).security)
    }

    @MultiEngineTest
    fun testSpecWideAlternatives(engine: ServerEngine) = test {
        val description = ApiDescription.create()
            .security(SecurityScheme.bearer(), SecurityScheme.apiKey("X-Gateway-Key"))

        val doc = getDocument(Inline.create().get { 42 }, engine, description)

        assertEquals("apiKey | bearer", getSecurity(doc.security))
    }

    @Test
    fun testSpecWideSecurityNeedsScheme() {
        assertFailsWith<IllegalArgumentException> { ApiDescription.create().security() }
    }

    // endregion

    // region Handlers (lambdas cannot be annotated, so they are declared as functions)

    @ApiResponse(ResponseStatus.Unauthorized, "Send your key")
    fun sendYourKey(): Int = 42

    @RequireRole("ADMIN")
    fun adminOnly(): Int = 42

    @RequireRole("ADMIN", "AUDITOR")
    fun adminOrAuditor(): Int = 42

    // endregion

    // region Helpers

    private suspend fun <T : HandlerBuilder.WithConcerns<T>> getDocument(api: T, engine: ServerEngine, description: OpenApiConcernBuilder? = null): OpenAPI =
        api.add(description ?: ApiDescription.create()).getOpenApi(engine).document!!

    private suspend fun getScheme(auth: ConcernBuilder, engine: ServerEngine): OpenApiSecurityScheme {
        val doc = getDocument(Inline.create().get { 42 }.add(auth), engine)

        return doc.components!!.securitySchemes!!.values.single()
    }

    private fun getOperations(doc: OpenAPI): List<Operation> =
        doc.paths.values.flatMap { it.readOperationsMap()?.values ?: emptyList() }

    /**
     * Formats the given requirements as "a+b | c", alternatives and schemes in alphabetical order.
     */
    private fun getSecurity(security: List<SecurityRequirement>?): String? {
        if (security == null) {
            return null
        }

        val alternatives = security.map { r -> r.keys.sorted().joinToString("+") }
            .sorted()

        return alternatives.joinToString(" | ")
    }

    private class DocumentingConcernBuilder(private val documentation: (OperationDocumentation) -> Unit) : ConcernBuilder {

        override fun build(content: Handler): Concern = DocumentingConcern(content, documentation)

    }

    private class DocumentingConcern(override val content: Handler, private val documentation: (OperationDocumentation) -> Unit) : DocumentedConcern {

        override fun addDocumentation(operation: OperationDocumentation) = documentation(operation)

        override suspend fun prepare(server: Server) = content.prepare(server)

        override suspend fun handle(request: Request): Response? = content.handle(request)

    }

    // endregion

}
