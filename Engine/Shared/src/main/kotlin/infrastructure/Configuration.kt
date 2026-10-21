package org.genhttp.engine.shared.infrastructure

import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.SslProtocol

import org.slf4j.ILoggerFactory

import java.net.InetAddress

/**
 * The configuration of a server instance.
 */
public data class ServerConfiguration(
    val developmentMode: Boolean,
    val endPoints: List<EndPointConfiguration>,
    val logging: ILoggerFactory
)

/**
 * The configuration of an endpoint to bind to.
 */
public data class EndPointConfiguration(
    val address: InetAddress?,
    val port: Int,
    val protocols: HttpProtocols,
    val dualStack: Boolean,
    val security: SecurityConfiguration?
)

/**
 * The TLS configuration of a secure endpoint.
 */
public data class SecurityConfiguration(
    val certificateProvider: CertificateProvider,
    val protocols: Set<SslProtocol>,
    val certificateValidator: CertificateValidator?
)
