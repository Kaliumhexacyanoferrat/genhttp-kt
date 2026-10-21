package org.genhttp.testing.acceptance.modules.clientcaching

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.clientcaching.ClientCache
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader

import kotlin.test.assertNotNull

class PolicyTests {

    @MultiEngineTest
    fun testExpireHeaderSet(engine: ServerEngine) = test {
        val content = Content.from(Resource.fromString("Content"))
            .add(ClientCache.policy().duration(1))

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertNotNull(response.getContentHeader("Expires"))
        }
    }

    @MultiEngineTest
    fun testExpireHeaderNotSetForOtherMethods(engine: ServerEngine) = test {
        val content = Content.from(Resource.fromString("Content"))
            .add(ClientCache.policy().duration(1))

        TestHost.run(content, engine = engine).use { runner ->
            val request = runner.getRequest(method = "HEAD")

            val response = runner.getResponse(request)

            AssertX.isNullOrEmpty(response.getContentHeader("Expires"))
        }
    }

    @MultiEngineTest
    fun testExpireHeaderNotSetForOtherStatus(engine: ServerEngine) = test {
        val content = Layout.create()
            .add(ClientCache.policy().duration(1))

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.isNullOrEmpty(response.getContentHeader("Expires"))
        }
    }

    @MultiEngineTest
    fun testPredicate(engine: ServerEngine) = test {
        val content = Content.from(Resource.fromString("Content"))
            .add(ClientCache.policy().duration(1).predicate { _, r -> r.content?.type != ContentType.TextPlain })

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.isNullOrEmpty(response.getContentHeader("Expires"))
        }
    }

}
