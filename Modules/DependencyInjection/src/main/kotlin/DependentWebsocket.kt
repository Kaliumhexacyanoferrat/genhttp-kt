@file:JvmName("DependentWebsocket")

package org.genhttp.modules.dependencyinjection

import org.genhttp.modules.dependencyinjection.infrastructure.HandlerResolver
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.imperative.ImperativeWebsocketBuilder
import org.genhttp.modules.websockets.reactive.ReactiveWebsocketBuilder

/**
 * Instructs the websocket handler to resolve the given type and all of its dependencies
 * from the service scope used by the server to handle incoming requests.
 *
 * @param T The type of handler to be resolved
 */
public inline fun <reified T : ImperativeHandler> ImperativeWebsocketBuilder.dependentHandler(): ImperativeWebsocketBuilder =
    handler { request -> HandlerResolver.obtain(request, T::class) }

/**
 * Instructs the websocket handler to resolve the given type and all of its dependencies
 * from the service scope used by the server to handle incoming requests.
 *
 * @param T The type of handler to be resolved
 */
public inline fun <reified T : ReactiveHandler> ReactiveWebsocketBuilder.dependentHandler(): ReactiveWebsocketBuilder =
    handler { request -> HandlerResolver.obtain(request, T::class) }
