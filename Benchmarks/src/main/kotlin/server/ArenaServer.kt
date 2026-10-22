package org.genhttp.benchmarks.server

import org.genhttp.api.content.io.Resource
import org.genhttp.benchmarks.benchmarks.webservices.Baseline
import org.genhttp.engine.internal.Host
import org.genhttp.modules.compression.compression
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.webservices.addService

import org.slf4j.helpers.NOPLoggerFactory

import kotlin.system.exitProcess

/**
 * Serves the baseline and pipeline endpoints the same way as the genhttp-kt entry of HttpArena does
 * (layout, webservices, compression, no logging), so that changes can be load tested end to end.
 */
suspend fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toInt() ?: 8080

    val app = Layout.create()
        .add("pipeline", Content.from(Resource.fromString("ok")))
        .addService<Baseline>("baseline11")
        .addService<Baseline>("baseline2")

    val host = Host.create()
        .handler(app)
        .compression()
        .logging(NOPLoggerFactory(), false)

    host.bind(null, port)

    exitProcess(host.run())
}
