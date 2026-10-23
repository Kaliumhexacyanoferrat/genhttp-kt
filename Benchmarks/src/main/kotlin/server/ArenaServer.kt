package org.genhttp.benchmarks.server

import kotlinx.coroutines.delay

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.benchmarks.benchmarks.webservices.Baseline
import org.genhttp.engine.internal.Host
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.Host as NettyHost
import org.genhttp.engine.netty.TcpTransportOptions
import org.genhttp.engine.netty.TlsProvider
import org.genhttp.modules.compression.compression
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.modules.websockets.Websocket

import org.slf4j.helpers.NOPLoggerFactory

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore

import kotlin.system.exitProcess

/**
 * Serves the baseline, pipeline and delay endpoints the same way as the genhttp-kt entry of HttpArena does
 * (layout, webservices, compression, no logging), so that changes can be load tested end to end.
 *
 * Arguments: the port (8080 by default), the engine (`internal` by default, `netty` for the Netty engine) and
 * optionally a PKCS#12 key store (password `genhttp`) to additionally serve HTTPS on port 8443 with. The TLS provider of
 * the Netty engine can be chosen with the environment variable `NETTY_TLS_PROVIDER` (`Auto`, `OpenSsl`, `Jdk`), its
 * cipher suites with `NETTY_CIPHER_SUITES` (TLS 1.3) and `NETTY_CIPHER_LIST` (TLS 1.2).
 */
suspend fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toInt() ?: 8080

    val netty = args.getOrNull(1).equals("netty", ignoreCase = true)

    val keyStore = args.getOrNull(2)

    val websocket = Websocket.imperative()
        .doNotAllocateFrameData()
        .handler(EchoHandler())

    val app = Layout.create()
        .add("pipeline", Content.from(Resource.fromString("ok")))
        .addService<Baseline>("baseline11")
        .addService<Baseline>("baseline2")
        .addService<Delay>("delay")
        .add("ws", websocket)

    // the TLS provider of the Netty engine can be selected for comparisons (Auto, OpenSsl, Jdk)
    val tlsProvider = System.getenv("NETTY_TLS_PROVIDER")?.let { TlsProvider.valueOf(it) } ?: TlsProvider.Auto

    val tcp = TcpTransportOptions(
        tlsProvider = tlsProvider,
        cipherSuites = System.getenv("NETTY_CIPHER_SUITES"),
        cipherList = System.getenv("NETTY_CIPHER_LIST")
    )

    val host = (if (netty) NettyHost.create(options = EngineOptions(tcp = tcp)) else Host.create())
        .handler(app)
        .compression()
        .logging(NOPLoggerFactory(), false)

    host.bind(null, port)

    if (keyStore != null) {
        host.bind(null, 8443, CertificateProvider.from(loadCertificate(Path.of(keyStore))), httpProtocols = HttpProtocols.Http1)
    }

    exitProcess(host.run())
}

/**
 * The async profile of HttpArena: holds the request for the given number of milliseconds.
 */
class Delay {

    @ResourceMethod(path = ":ms")
    suspend fun wait(ms: Int): String {
        if (ms > 0) {
            delay(ms.toLong())
        }

        return ms.toString()
    }

}

/**
 * Loads the first key entry of a PKCS#12 key store protected with the password `genhttp`.
 */
private fun loadCertificate(file: Path): KeyStore.PrivateKeyEntry {
    val password = "genhttp".toCharArray()

    val store = KeyStore.getInstance("PKCS12")
    Files.newInputStream(file).use { store.load(it, password) }

    val alias = store.aliases().toList().first { store.isKeyEntry(it) }

    return store.getEntry(alias, KeyStore.PasswordProtection(password)) as KeyStore.PrivateKeyEntry
}
