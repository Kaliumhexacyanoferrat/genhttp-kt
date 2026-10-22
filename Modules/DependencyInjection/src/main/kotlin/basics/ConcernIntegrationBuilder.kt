package org.genhttp.modules.dependencyinjection.basics

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.modules.dependencyinjection.DependentConcern

import kotlin.reflect.KClass

internal class ConcernIntegrationBuilder<T : DependentConcern>(private val type: KClass<T>) : ConcernBuilder {

    override fun build(content: Handler): Concern = ConcernIntegration(content, type)

}
