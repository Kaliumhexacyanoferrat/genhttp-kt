package org.genhttp.benchmarks.infrastructure

import java.nio.file.Files
import java.nio.file.Path

/**
 * Provides the files of the `Resources` folder (copied to the output directory in GenHTTP) as a directory.
 */
object BenchmarkResources {

    private val ResourceFiles = listOf("file.js", "file.js.br")

    val directory: Path by lazy {
        val target = Files.createTempDirectory("genhttp-benchmarks")

        for (file in ResourceFiles) {
            BenchmarkResources::class.java.getResourceAsStream("/Resources/$file")!!.use { input ->
                Files.copy(input, target.resolve(file))
            }
        }

        target
    }

}
