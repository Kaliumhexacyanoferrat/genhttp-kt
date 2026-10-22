package org.genhttp.testing.acceptance.modules.websockets.protocol

import org.genhttp.api.protocol.Request
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.websockets.SocketConnection
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.provider.ConnectionSettings

import java.nio.ByteBuffer

class MockConnection : SocketConnection {

    override val request: Request
        get() = throw NotImplementedError()

    override val settings: ConnectionSettings
        get() = ConnectionSettings(Formatting.default().build(), JsonFormat(), true, true)

    override suspend fun write(payload: ByteBuffer, opcode: FrameType, fin: Boolean, flush: Boolean): Unit = throw NotImplementedError()

    override suspend fun ping(flush: Boolean): Unit = throw NotImplementedError()

    override suspend fun pong(payload: ByteBuffer, flush: Boolean): Unit = throw NotImplementedError()

    override suspend fun pong(flush: Boolean): Unit = throw NotImplementedError()

    override suspend fun close(reason: String?, statusCode: Int, flush: Boolean): Unit = throw NotImplementedError()

    override suspend fun flush(): Unit = throw NotImplementedError()

}
