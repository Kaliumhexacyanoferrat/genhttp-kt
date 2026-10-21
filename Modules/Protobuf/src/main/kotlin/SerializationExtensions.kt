@file:JvmName("SerializationExtensions")

package org.genhttp.modules.protobuf

import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.conversion.serializers.SerializationBuilder
import org.genhttp.modules.protobuf.providers.ProtobufFormat

/**
 * Adds the protobuf format to the serialization registry.
 *
 * Models are serialized with kotlinx.serialization (code first, like protobuf-net upstream), so they need to be
 * annotated with `@Serializable` (field numbers via `@ProtoNumber`).
 */
public fun SerializationBuilder.addProtobuf(): SerializationBuilder = add(ContentType.ApplicationProtobuf, ProtobufFormat())
