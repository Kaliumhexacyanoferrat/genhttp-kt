package org.genhttp.modules.websockets.reactive

import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.provider.WebsocketBuilder
import org.genhttp.modules.websockets.provider.WebsocketHandler

/**
 * Builds a websocket handler that dispatches incoming frames to a [ReactiveHandler].
 */
public class ReactiveWebsocketBuilder : WebsocketBuilder<ReactiveWebsocketBuilder>() {

    private var handlerFactory: ((Request) -> ReactiveHandler)? = null

    /**
     * Sets the handler to react to incoming message frames.
     *
     * @param handler The handler to react to incoming message frames.
     */
    public fun handler(handler: ReactiveHandler): ReactiveWebsocketBuilder = handler { handler }

    /**
     * Sets the handler to react to incoming message frames (created per connection).
     *
     * @param T The type of handler to be used (needs a public constructor without parameters)
     */
    public inline fun <reified T : ReactiveHandler> handler(): ReactiveWebsocketBuilder =
        handler { T::class.java.getDeclaredConstructor().newInstance() }

    /**
     * Sets the handler factory to react to incoming message frames.
     *
     * @param handlerFactory The handler factory to react to incoming message frames.
     */
    public fun handler(handlerFactory: (Request) -> ReactiveHandler): ReactiveWebsocketBuilder {
        this.handlerFactory = handlerFactory
        return this
    }

    override fun build(): Handler {
        val factory = handlerFactory ?: throw BuilderMissingPropertyException("HandlerFactory")

        val contentFactory = { request: Request -> ReactiveWebsocketContent(factory(request), request, buildSettings()) }

        return Concerns.chain(concerns, WebsocketHandler(contentFactory))
    }

}
