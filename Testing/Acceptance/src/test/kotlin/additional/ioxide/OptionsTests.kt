package org.genhttp.testing.acceptance.additional.ioxide

import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.ReactorOptions
import org.genhttp.engine.ioxide.TcpTransportOptions
import org.genhttp.engine.ioxide.TlsProvider

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Not ported from GenHTTP: the options of the ioxide engine default to those of the ioxide engine of GenHTTP
 * (timeouts that would affect websockets or large downloads are disabled) and reject values ioxide cannot work with.
 */
class OptionsTests {

    @Test
    fun testDefaults() {
        val options = EngineOptions()

        val reactor = options.reactor

        assertEquals(Runtime.getRuntime().availableProcessors(), reactor.reactorCount)
        assertEquals(8192, reactor.ringEntries)
        assertEquals(32 * 1024, reactor.recvBufferSize)
        assertEquals(4096, reactor.recvSlots)

        val tcp = options.tcp

        assertEquals(10_000, tcp.handshakeTimeoutMs)
        assertNull(tcp.cipherSuites)
        assertNull(tcp.cipherList)
        assertFalse(tcp.txKernelTls)
        assertFalse(tcp.rxKernelTls)
        assertEquals(1024, tcp.listenBacklog)
        assertEquals(16 * 1024, tcp.writeSlabSize)
        assertEquals(1024, tcp.poolMax)
        assertEquals(64, tcp.recvQueueEntries)
        assertEquals(0, tcp.idleTimeoutMs)
        assertEquals(0, tcp.sendTimeoutMs)
        assertEquals(TlsProvider.Auto, tcp.tlsProvider)
    }

    @Test
    fun testReactorOptionsAreValidated() {
        assertEquals("At least one reactor is required", assertFailsWith<IllegalArgumentException> { ReactorOptions(reactorCount = 0) }.message)
        assertEquals("The ring entries must be positive", assertFailsWith<IllegalArgumentException> { ReactorOptions(ringEntries = 0) }.message)
        assertEquals("The receive buffer size must be positive", assertFailsWith<IllegalArgumentException> { ReactorOptions(recvBufferSize = 0) }.message)

        for (slots in listOf(0, 3000, 65536)) {
            assertEquals("The receive slots must be a power of two up to 32768", assertFailsWith<IllegalArgumentException> { ReactorOptions(recvSlots = slots) }.message)
        }
    }

    @Test
    fun testTcpOptionsAreValidated() {
        assertEquals("The handshake timeout must be positive", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(handshakeTimeoutMs = 0) }.message)
        assertEquals("The listen backlog must be positive", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(listenBacklog = 0) }.message)
        assertEquals("The write slab size must be positive", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(writeSlabSize = 0) }.message)
        assertEquals("The pool size must not be negative", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(poolMax = -1) }.message)
        assertEquals("The receive queue entries must be a power of two", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(recvQueueEntries = 48) }.message)
        assertEquals("The idle timeout must not be negative", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(idleTimeoutMs = -1) }.message)
        assertEquals("The send timeout must not be negative", assertFailsWith<IllegalArgumentException> { TcpTransportOptions(sendTimeoutMs = -1) }.message)

        assertEquals(
            "Kernel TLS for received records requires kernel TLS for sent records",
            assertFailsWith<IllegalArgumentException> { TcpTransportOptions(rxKernelTls = true) }.message
        )
    }

    @Test
    fun testTimeoutsCanBeDisabled() {
        val options = TcpTransportOptions(idleTimeoutMs = 0, sendTimeoutMs = 0, handshakeTimeoutMs = 1, listenBacklog = 1, poolMax = 0)

        assertEquals(0, options.idleTimeoutMs)
        assertEquals(0, options.sendTimeoutMs)
        assertEquals(0, options.poolMax)
    }

}
