package org.genhttp.modules.reflection.operations

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.routing.OperationRoute

import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * A service method that can be invoked by the framework, including all information required to do so.
 *
 * @property method The function to be invoked (for inline lambdas, a function describing the lambda's signature)
 * @property delegate The lambda to be invoked, if the operation has been declared via an inline lambda
 * @property executionSettings The settings used to execute the function
 * @property configuration The configuration of the operation (e.g. the supported request methods)
 * @property route The route the operation is reachable at
 * @property result Describes how the result of the function is processed
 * @property arguments The arguments of the function, by their name
 * @property interceptors The interceptors to be invoked before the function
 */
public class Operation(
    public val method: KFunction<*>,
    public val delegate: Function<*>?,
    public val executionSettings: ExecutionSettings,
    public val configuration: MethodConfiguration,
    public val route: OperationRoute,
    public val result: OperationResult,
    public val arguments: Map<String, OperationArgument>,
    public val interceptors: List<OperationInterceptor>
)
