@file:JvmName("Integration")

package org.genhttp.modules.dependencyinjection

import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.api.infrastructure.tryGet
import org.genhttp.api.protocol.Request
import org.genhttp.modules.dependencyinjection.infrastructure.InjectionConcernBuilder

import org.koin.core.Koin
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

private const val PROVIDER_VAR = "__DI_SERVICE_PROVIDER"

private const val SCOPE_VAR = "__DI_SERVICE_SCOPE"

/**
 * The qualifier of the scope that is created by the server for every request. Definitions that
 * should live as long as a request (the counterpart of scoped services in other containers)
 * need to be declared within this scope:
 *
 * ```kotlin
 * module {
 *     scope(RequestScope) { scoped { RequestTracker() } }
 * }
 * ```
 */
public val RequestScope: Qualifier = named("genhttp.request")

/**
 * Uses the given container to inject dependencies within the server instance.
 *
 * @param koin The container to be used for injection
 * @return The updated host
 */
public fun ServerHost.addDependencyInjection(koin: Koin): ServerHost = add(InjectionConcernBuilder(koin))

/**
 * Retrieves the container used to resolve dependencies from the given server instance.
 *
 * @return The container retrieved from the server instance
 * @throws IllegalStateException Thrown if dependency injection is not enabled on the host
 */
public fun Server.getServiceProvider(): Koin =
    properties.tryGet<Koin>(PROVIDER_VAR)
        ?: throw IllegalStateException("Unable to retrieve service provider from the request. Ensure dependency injection has been configured.")

/**
 * Retrieves the container used to resolve dependencies from the given request.
 *
 * @return The container retrieved from the request
 * @throws IllegalStateException Thrown if dependency injection is not enabled on the host
 */
public fun Request.getServiceProvider(): Koin = server.getServiceProvider()

/**
 * Retrieves the scope used to resolve dependencies from the given request.
 *
 * @return The scope retrieved from the request
 * @throws IllegalStateException Thrown if dependency injection is not enabled on the host
 */
public fun Request.getServiceScope(): Scope =
    properties.tryGet<Scope>(SCOPE_VAR)
        ?: throw IllegalStateException("Unable to retrieve service scope from the request. Ensure dependency injection has been configured.")

internal fun Server.configure(koin: Koin) {
    properties[PROVIDER_VAR] = koin
}

internal fun Request.configure(scope: Scope) {
    properties[SCOPE_VAR] = scope
}
