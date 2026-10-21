package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.Constants

import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.Socket

abstract class WireTest {

    protected val NL: String = "\r\n"

    protected suspend fun assertWire(request: List<String>, assertion: String, handler: HandlerBuilder? = null, engine: ServerEngine = ServerEngine.Internal): Unit =
        assertWire(request.joinToString(NL) + NL, assertion, handler, engine)

    protected suspend fun assertWire(request: String, assertion: String, handler: HandlerBuilder? = null, engine: ServerEngine = ServerEngine.Internal) {
        TestHost.run(handler ?: Layout.create(), engine = engine).use { host ->
            val result = send(host) { w ->
                w.write(request)
                w.write(NL)
            }

            AssertX.contains(assertion, result)
        }
    }

    protected fun send(host: TestHost, sender: (Writer) -> Unit): String {
        Socket("127.0.0.1", host.port).use { client ->
            client.soTimeout = Constants.ClientTimeout

            val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)

            sender(writer)

            writer.flush()

            return InputStreamReader(client.getInputStream(), Charsets.UTF_8).readText()
        }
    }

}
