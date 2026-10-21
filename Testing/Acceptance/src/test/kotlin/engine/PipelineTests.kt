package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket

import kotlin.test.assertEquals

class PipelineTests {

    @MultiEngineTest
    fun serverSupportsPipelining(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            Socket("127.0.0.1", runner.port).use { client ->
                client.soTimeout = Constants.ClientTimeout

                val stream = client.getInputStream()

                val count = 10

                writeRequests(client.getOutputStream(), count)

                readRequests(stream, count, "Hello World!")
            }
        }
    }

    private fun writeRequests(stream: OutputStream, count: Int) {
        val builder = StringBuilder()

        for (i in 0 until count) {
            builder.append("GET / HTTP/1.1\r\n")
            builder.append("Host: 128.0.0.1\r\n")
            builder.append("Connection: Keep-Alive\r\n\r\n")
        }

        stream.write(builder.toString().toByteArray(Charsets.UTF_8))

        stream.flush()
    }

    private fun readRequests(stream: InputStream, count: Int, searchFor: String) {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))

        var found = 0

        val result = StringBuilder()

        try {
            while (true) {
                val line = reader.readLine() ?: break

                if (line.contains(searchFor)) {
                    found++
                }

                result.appendLine(line)
            }
        } catch (_: IOException) {
        }

        assertEquals(count - 1, found) // last body does not end with \r\n
    }

}
