package org.genhttp.modules.dependencyinjection.basics

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.dependencyinjection.DependentHandler

import kotlin.reflect.KClass

internal class HandlerIntegrationBuilder<T : DependentHandler>(private val type: KClass<T>) : HandlerBuilder {

    override fun build(): Handler = HandlerIntegration(type)

}
