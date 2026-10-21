@file:JvmName("Concern")

package org.genhttp.modules.io

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.providers.InlineConcernBuilder

/**
 * Creates a concern from the given logic that can be added to
 * other handlers as needed.
 *
 * @param logic The logic to be executed
 * @return The newly created builder
 */
public fun Concern.Companion.from(logic: suspend (Request, Handler) -> Response?): InlineConcernBuilder =
    InlineConcernBuilder().logic(logic)
