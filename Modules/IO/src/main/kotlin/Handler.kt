@file:JvmName("Handler")

package org.genhttp.modules.io

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.providers.InlineHandlerBuilder

/**
 * Creates a handler from the given logic.
 *
 * @param logic The logic to be executed
 * @return The newly created builder
 */
public fun Handler.Companion.from(logic: suspend (Request) -> Response?): InlineHandlerBuilder =
    InlineHandlerBuilder().logic(logic)
