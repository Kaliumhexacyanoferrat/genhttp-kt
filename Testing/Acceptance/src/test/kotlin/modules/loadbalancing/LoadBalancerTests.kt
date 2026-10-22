package org.genhttp.testing.acceptance.modules.loadbalancing

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.loadbalancing.LoadBalancer
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import kotlin.test.assertEquals

class LoadBalancerTests {

    @MultiEngineTest
    fun testProxy(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Proxy!")), engine = engine).use { upstream ->
            val loadbalancer = LoadBalancer.create()
                .proxy("http://localhost:${upstream.port}")

            TestHost.run(loadbalancer).use { runner ->
                val response = runner.getResponse()

                response.assertStatus(ResponseStatus.Ok)
                assertEquals("Proxy!", response.getContent())
            }
        }
    }

    @MultiEngineTest
    fun testRedirect(engine: ServerEngine) = test {
        val loadbalancer = LoadBalancer.create()
            .redirect("http://node")

        TestHost.run(loadbalancer, engine = engine).use { runner ->
            val response = runner.getResponse("/page")

            response.assertStatus(ResponseStatus.TemporaryRedirect)
            assertEquals("http://node/page", response.getHeader("Location"))
        }
    }

    @MultiEngineTest
    fun testCustomHandler(engine: ServerEngine) = test {
        val loadbalancer = LoadBalancer.create()
            .add(Content.from(Resource.fromString("My Content!")))

        TestHost.run(loadbalancer, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("My Content!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testPriorities(engine: ServerEngine) = test {
        val loadbalancer = LoadBalancer.create()
            .add(Content.from(Resource.fromString("Prio A")), { Priority.High })
            .add(Content.from(Resource.fromString("Prio B")), { Priority.Low })

        TestHost.run(loadbalancer, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("Prio A", response.getContent())
        }
    }

    @MultiEngineTest
    fun testMultiplePriorities(engine: ServerEngine) = test {
        val loadbalancer = LoadBalancer.create()
            .add(Content.from(Resource.fromString("Prio A1")), { Priority.High })
            .add(Content.from(Resource.fromString("Prio A2")), { Priority.High })
            .add(Content.from(Resource.fromString("Prio A3")), { Priority.High })

        TestHost.run(loadbalancer, engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.startsWith("Prio A", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNoNodes(engine: ServerEngine) = test {
        TestHost.run(LoadBalancer.create(), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

}
