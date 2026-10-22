package org.genhttp.modules.websockets.provider

import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationFormat

/**
 * Provides the settings used by a websocket connection.
 *
 * @param formatters The formatter registry to be used to send and receive formatted data
 * @param serializationFormat The serialization handler to send and receive structured data
 * @param handleContinuationFramesManually Whether the API user is expected to handle continuation frames manually
 * @param allocateFrameData Whether data will automatically be allocated so it can still be accessed after the next message has been read
 */
public data class ConnectionSettings(
    val formatters: FormatterRegistry,
    val serializationFormat: SerializationFormat,
    val handleContinuationFramesManually: Boolean,
    val allocateFrameData: Boolean
)
