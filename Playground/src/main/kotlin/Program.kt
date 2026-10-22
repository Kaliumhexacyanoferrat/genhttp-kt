package org.genhttp.playground

import org.genhttp.engine.internal.Host
import org.genhttp.modules.practices.defaults
import org.genhttp.playground.samples.LayoutingSample

import kotlin.system.exitProcess

suspend fun main() {
    val sample = LayoutingSample.create()

    val result = Host.create()
        .handler(sample)
        .development()
        .defaults()
        .run()

    exitProcess(result)
}
