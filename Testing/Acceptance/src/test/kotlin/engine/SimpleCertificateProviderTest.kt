package org.genhttp.testing.acceptance.engine

import org.genhttp.engine.shared.security.SimpleCertificateProvider
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Security

import org.junit.jupiter.api.Test

import kotlin.test.assertNotNull

class SimpleCertificateProviderTest {

    @Test
    fun testProvider() = test {
        // a real certificate is used as the JVM certificate types cannot be mocked without additional libraries
        val cert = Security.getCertificate()

        val provider = SimpleCertificateProvider(cert)

        assertNotNull(provider.provide("google.com"))
    }

}
