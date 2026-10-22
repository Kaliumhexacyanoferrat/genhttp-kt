package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler

import org.koin.core.Koin

internal class InjectionConcernBuilder(private val koin: Koin) : ConcernBuilder {

    override fun build(content: Handler): Concern = InjectionConcern(content, koin)

}
