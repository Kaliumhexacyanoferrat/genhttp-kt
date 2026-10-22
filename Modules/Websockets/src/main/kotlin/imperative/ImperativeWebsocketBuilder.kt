package org.genhttp.modules.websockets.imperative

import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.provider.WebsocketBuilder
import org.genhttp.modules.websockets.provider.WebsocketHandler

/**
 * Builds a websocket handler that hands the connection over to an [ImperativeHandler].
 */
public class ImperativeWebsocketBuilder : WebsocketBuilder<ImperativeWebsocketBuilder>() {

    private var handlerFactory: ((Request) -> ImperativeHandler)? = null

    /**
     * Sets the handler used to establish a message pump.
     *
     * @param handler The handler used to establish a message pump
     */
    public fun handler(handler: ImperativeHandler): ImperativeWebsocketBuilder = handler { handler }

    /**
     * Sets the handler used to establish a message pump (created per connection).
     *
     * @param T The type of handler to be used (needs a public constructor without parameters)
     */
    public inline fun <reified T : ImperativeHandler> handler(): ImperativeWebsocketBuilder =
        handler { T::class.java.getDeclaredConstructor().newInstance() }

    /**
     * Sets the handler factory used to establish a message pump.
     *
     * @param handlerFactory The handler factory used to establish a message pump
     */
    public fun handler(handlerFactory: (Request) -> ImperativeHandler): ImperativeWebsocketBuilder {
        this.handlerFactory = handlerFactory
        return this
    }

    override fun build(): Handler {
        val factory = handlerFactory ?: throw BuilderMissingPropertyException("HandlerFactory")

        val contentFactory = { request: Request -> ImperativeWebsocketContent(factory(request), request, buildSettings()) }

        return Concerns.chain(concerns, WebsocketHandler(contentFactory))
    }

}
