package org.genhttp.modules.reflection.operations

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.routing.OperationRoute

import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * Describes how the result of an operation is sent to the client.
 */
public enum class OperationResultSink {

    /** The result is converted using a formatter (e.g. numbers). */
    Formatter,

    /** The result is serialized (e.g. to JSON). */
    Serializer,

    /** The result is a handler or response that is used as-is. */
    Dynamic,

    /** The result is binary data (a stream, byte array or buffer). */
    Binary,

    /** The operation does not return a result. */
    None

}

/**
 * The result of an operation.
 *
 * @property type The type of the result
 * @property sink The way the result is sent to the client
 */
public class OperationResult(public val type: KType, public val sink: OperationResultSink)
