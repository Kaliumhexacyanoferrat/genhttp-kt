package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ClientProtocol
import org.genhttp.api.protocol.Request
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.net.InetAddress
import java.security.cert.X509Certificate

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientConnectionTests {

    @MultiEngineTest
    fun testClientIsExposed(engine: ServerEngine) = test {
        // the client connection is a pooled instance that gets reset once the request has
        // been handled, so its state must be captured immediately while handling the request
        var address: InetAddress? = null
        var protocol: ClientProtocol? = null
        var certificate: X509Certificate? = null

        val handler = Inline.create().get { r: Request ->
            address = r.client.address
            protocol = r.client.protocol
            certificate = r.client.certificate

            r.respond().build()
        }

        TestHost.run(handler, engine = engine).use { runner ->
            runner.getResponse()

            assertTrue(address!!.isLoopbackAddress)
            assertEquals(ClientProtocol.Http, protocol)
            assertNull(certificate)
        }
    }

    @MultiEngineTest
    fun testClientIsResetBetweenRequests(engine: ServerEngine) = test {
        // the client connection is a pooled instance reused across requests on the same
        // connection, so its state must be captured immediately while handling each request
        val addresses = mutableListOf<InetAddress?>()

        val handler = Inline.create().get { r: Request ->
            addresses.add(r.client.address)

            r.respond().build()
        }

        TestHost.run(handler, engine = engine).use { runner ->
            runner.getResponse()
            runner.getResponse()

            assertEquals(2, addresses.size)

            assertTrue(addresses[0]!!.isLoopbackAddress)
            assertTrue(addresses[1]!!.isLoopbackAddress)
        }
    }

}
