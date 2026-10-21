package org.genhttp.testing.acceptance.api

import org.genhttp.modules.conversion.Serialization

import org.junit.jupiter.api.Test

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import kotlin.test.assertTrue

class ContentTypeTests {

    @Test
    fun testParallelAccess() {
        val executor = Executors.newFixedThreadPool(10)

        try {
            val tasks = (0 until 10).map {
                executor.submit {
                    Serialization.default()
                }
            }

            // propagates exceptions thrown by any of the parallel invocations
            tasks.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }

        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
    }

}
