package org.genhttp.modules.layouting.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder

internal class HandlerWrapper(private val handler: Handler) : HandlerBuilder {

    override fun build(): Handler = handler

}
