package org.genhttp.modules.functional.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodCollection
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ServiceMethodProvider
import org.genhttp.modules.reflection.operations.OperationBuilder

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

/**
 * A function registered with an inline builder.
 *
 * @property path The path the function is reachable at, if any
 * @property configuration The request methods handled by the function
 * @property delegate The suspend lambda or function reference implementing the handler
 */
public data class InlineFunction(val path: String?, val configuration: MethodConfiguration, val delegate: Function<*>)
