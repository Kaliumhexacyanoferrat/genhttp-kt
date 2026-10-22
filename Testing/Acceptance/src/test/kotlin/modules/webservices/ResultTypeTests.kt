package org.genhttp.testing.acceptance.modules.webservices

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

// region Supporting data structures

open class TestResource {

    @ResourceMethod(path = "task")
    suspend fun asyncTask() {
    }

    @ResourceMethod(path = "value-task")
    suspend fun asyncValueTask() {
    }

    @ResourceMethod(path = "generic-task")
    suspend fun asyncGenericTask(): String = "Task result"

    @ResourceMethod(path = "generic-value-task")
    suspend fun asyncGenericValueTask(): String = "ValueTask result"

}

// endregion

open class ResultTypeTests {

    // region Helpers

    private suspend fun getRunner(engine: ServerEngine, mode: ExecutionMode): TestHost = TestHost.run(
        Layout.create().addService<TestResource>(
            "t",
            serializers = Serialization.default(),
            injectors = Injection.default(),
            formatters = Formatting.default(),
            mode = mode
        ), engine = engine
    )

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun controllerMayReturnTask(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/task")

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnValueTask(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/value-task")

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnGenericTask(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/generic-task")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Task result", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnGenericValueTask(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/generic-value-task")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("ValueTask result", response.getContent())
        }
    }

    // endregion

}
