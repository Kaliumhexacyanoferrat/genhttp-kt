@file:JvmName("ConnectionSerializationExtensions")

package org.genhttp.modules.websockets

import org.genhttp.modules.websockets.protocol.FrameType

import java.nio.ByteBuffer

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Serializes the given payload using the serialization format passed
 * to the web socket builder and sends it as a websocket frame.
 *
 * @param payload The data to be sent
 * @param opcode The type of data to be sent (either text or binary)
 * @param T The type of data to be sent
 * @throws IllegalStateException Thrown, if no serialization format was passed to the websocket handler builder
 * @throws IllegalArgumentException Thrown, if null has been passed as a payload
 */
public suspend inline fun <reified T> SocketConnection.writePayload(payload: T, opcode: FrameType = FrameType.Text) {
    writePayloadOf(payload, typeOf<T>(), opcode)
}

@PublishedApi
internal suspend fun SocketConnection.writePayloadOf(payload: Any?, type: KType, opcode: FrameType) {
    requireNotNull(payload) { "payload" }

    val clazz = type.classifier as? KClass<*>

    if (clazz != null && settings.formatters.canHandle(clazz)) {
        val data = settings.formatters.write(payload, clazz) ?: ""

        write(ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8)), opcode)

        return
    }

    write(settings.serializationFormat.serialize(payload), opcode)
}
