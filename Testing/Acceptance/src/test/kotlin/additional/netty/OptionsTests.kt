package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.TcpTransportOptions
import org.genhttp.engine.netty.TlsProvider

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Not ported from GenHTTP: the TCP options of the Netty engine (`TcpTransportOptions` of ioxide) reject values that
 * cannot work and default to the values of ioxide (timeouts that would affect websockets or large downloads are
 * disabled).
 */
class OptionsTests {

    @Test
    fun testDefaults() {
        val options = EngineOptions().tcp

        assertEquals(10.seconds, options.handshakeTimeout)
        assertNull(options.cipherSuites)
        assertNull(options.cipherList)
        assertEquals(TlsProvider.Auto, options.tlsProvider)
        assertEquals(1024, options.listenBacklog)
        assertEquals(Duration.ZERO, options.idleTimeout)
        assertEquals(Duration.ZERO, options.sendTimeout)
    }

    @Test
    fun testHandshakeTimeoutMustBePositive() {
        val zero = assertFailsWith<IllegalArgumentException> { TcpTransportOptions(handshakeTimeout = Duration.ZERO) }

        assertEquals("The handshake timeout must be positive", zero.message)

        assertFailsWith<IllegalArgumentException> { TcpTransportOptions(handshakeTimeout = (-1).seconds) }
    }

    @Test
    fun testListenBacklogMustBePositive() {
        val zero = assertFailsWith<IllegalArgumentException> { TcpTransportOptions(listenBacklog = 0) }

        assertEquals("The listen backlog must be positive", zero.message)

        assertFailsWith<IllegalArgumentException> { TcpTransportOptions(listenBacklog = -1) }
    }

    @Test
    fun testIdleTimeoutMustNotBeNegative() {
        val negative = assertFailsWith<IllegalArgumentException> { TcpTransportOptions(idleTimeout = (-1).milliseconds) }

        assertEquals("The idle timeout must not be negative", negative.message)
    }

    @Test
    fun testSendTimeoutMustNotBeNegative() {
        val negative = assertFailsWith<IllegalArgumentException> { TcpTransportOptions(sendTimeout = (-1).milliseconds) }

        assertEquals("The send timeout must not be negative", negative.message)
    }

    @Test
    fun testTimeoutsCanBeDisabled() {
        val options = TcpTransportOptions(idleTimeout = Duration.ZERO, sendTimeout = Duration.ZERO, handshakeTimeout = 1.milliseconds, listenBacklog = 1)

        assertEquals(Duration.ZERO, options.idleTimeout)
        assertEquals(Duration.ZERO, options.sendTimeout)
    }

}
