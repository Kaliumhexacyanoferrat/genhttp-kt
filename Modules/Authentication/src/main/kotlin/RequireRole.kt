package org.genhttp.modules.authentication

import org.genhttp.modules.authentication.roles.RoleInterceptor
import org.genhttp.modules.reflection.InterceptWith

/**
 * When annotated on a service method, requests will only be allowed if the authenticated user has the specified
 * roles (`RequireRoleAttribute` in GenHTTP).
 *
 * @property roles The roles which need to be present in order to let the request pass
 */
@InterceptWith(RoleInterceptor::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
public annotation class RequireRole(vararg val roles: String)
