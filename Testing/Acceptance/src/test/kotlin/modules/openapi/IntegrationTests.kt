package org.genhttp.testing.acceptance.modules.openapi

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.addController
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.reflection.Method
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals

class IntegrationTests {

    // region Tests

    @MultiEngineTest
    fun testWebserviceSupported(engine: ServerEngine) = test {
        val api = Layout.create()
            .addService<MyService>("my")
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertEquals("/my/", doc?.paths?.entries?.first()?.key)
    }

    @MultiEngineTest
    fun testControllerSupported(engine: ServerEngine) = test {
        val api = Layout.create()
            .addController<MyController>("my")
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertEquals("/my/method/", doc?.paths?.entries?.first()?.key)
    }

    @MultiEngineTest
    fun testInlineSupported(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/method") { 42 }
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertEquals("/method", doc?.paths?.entries?.first()?.key)
    }

    @MultiEngineTest
    fun testObsoleteOperation(engine: ServerEngine) = test {
        val api = Layout.create()
            .addService<ObsoleteService>("my")
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertEquals(true, doc?.paths?.entries?.first()?.value?.readOperationsMap()?.values?.first()?.deprecated)
    }

    @MultiEngineTest
    fun testControllerWithMultipleMethods(engine: ServerEngine) = test {
        val api = Layout.create()
            .addController<MultipleMethodsController>("my")
            .add(ApiDescription.create())

        val doc = api.getOpenApi(engine).document

        assertEquals(2, doc?.paths?.getValue("/my/method/")?.readOperationsMap()?.size)
    }

    // endregion

    // region Supporting data structures

    class MyService {

        @ResourceMethod
        fun method(): Int = 42

    }

    class ObsoleteService {

        @Deprecated("Don't use this anymore")
        @ResourceMethod(path = "obsolete")
        fun obsolete(): Int = 43

    }

    class MyController {

        @ControllerAction(Method.Get)
        fun method(): Int = 42

    }

    class MultipleMethodsController {

        @ControllerAction(Method.Get, Method.Options)
        fun method(): Int = 42

    }

    // endregion

}
