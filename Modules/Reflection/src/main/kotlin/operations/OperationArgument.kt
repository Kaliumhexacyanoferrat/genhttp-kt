package org.genhttp.modules.reflection.operations

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.routing.OperationRoute

import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * The source an argument of an operation is read from.
 */
public enum class OperationArgumentSource {

    /** The argument is read from the request path. */
    Path,

    /** The argument is read from the query string. */
    Query,

    /** The argument is read from the request body as a plain value. */
    Body,

    /** The argument is provided by an injector. */
    Injected,

    /** The argument is deserialized from the request body. */
    Content,

    /** The argument is the request body as a stream. */
    Streamed

}

/**
 * An argument of an operation.
 *
 * GenHTTP stores the default value of the parameter. Kotlin cannot read default values via reflection, so the
 * argument knows whether the parameter is optional instead; optional arguments without a value are omitted from
 * the invocation so that the declared default applies.
 *
 * @property name The name of the parameter
 * @property type The type of the parameter
 * @property source The source of the value
 * @property isOptional Whether the parameter declares a default value
 */
public class OperationArgument(
    name: String,
    public val type: KType,
    public val source: OperationArgumentSource,
    public val isOptional: Boolean = false
) {

    /**
     * The name of the parameter.
     */
    public val name: ByteString = ByteString(name)

}
