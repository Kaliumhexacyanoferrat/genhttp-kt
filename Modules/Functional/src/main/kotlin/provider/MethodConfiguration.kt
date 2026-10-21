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
 * The request methods handled by an inline function (`MethodConfiguration` in GenHTTP, renamed to avoid a clash
 * with the interface of the reflection module).
 */
public data class InlineMethodConfiguration(override val supportedMethods: Set<RequestMethod>) : MethodConfiguration
