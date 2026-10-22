@file:JvmName("WebsocketFrameSerializationExtensions")

package org.genhttp.modules.websockets

import org.genhttp.api.protocol.ByteString

import java.io.ByteArrayInputStream

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Tries to read the payload of the web socket frame into an object of the specified type.
 *
 * @param T The expected type of data
 * @return The deserialized data
 * @throws IllegalStateException Thrown, if no serialization format has been passed to the handler builder or the deserialization attempt fails
 */
public suspend inline fun <reified T> WebsocketFrame.readPayload(): T = readPayloadOf(typeOf<T>()) as T

@PublishedApi
internal suspend fun WebsocketFrame.readPayloadOf(type: KType): Any? {
    val settings = connection.settings

    val bytes = ByteArray(data.remaining())

    data.get(bytes)

    val clazz = type.classifier as? KClass<*>

    if (clazz != null && settings.formatters.canHandle(clazz)) {
        return settings.formatters.read(ByteString(bytes), clazz)
    }

    return settings.serializationFormat.deserialize(ByteArrayInputStream(bytes), type)
}
