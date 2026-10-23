package org.genhttp.benchmarks.server

import kotlinx.coroutines.delay

import org.genhttp.api.content.io.Resource
import org.genhttp.benchmarks.benchmarks.webservices.Baseline
import org.genhttp.engine.internal.Host
import org.genhttp.engine.netty.Host as NettyHost
import org.genhttp.modules.compression.compression
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService

import org.slf4j.helpers.NOPLoggerFactory

import kotlin.system.exitProcess

/**
 * Serves the baseline, pipeline and delay endpoints the same way as the genhttp-kt entry of HttpArena does
 * (layout, webservices, compression, no logging), so that changes can be load tested end to end.
 *
 * Arguments: the port (8080 by default) and the engine (`internal` by default, `netty` for the Netty engine).
 */
suspend fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toInt() ?: 8080

    val netty = args.getOrNull(1).equals("netty", ignoreCase = true)

    val app = Layout.create()
        .add("pipeline", Content.from(Resource.fromString("ok")))
        .addService<Baseline>("baseline11")
        .addService<Baseline>("baseline2")
        .addService<Delay>("delay")

    val host = (if (netty) NettyHost.create() else Host.create())
        .handler(app)
        .compression()
        .logging(NOPLoggerFactory(), false)

    host.bind(null, port)

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
