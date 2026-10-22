package org.genhttp.modules.authentication.roles

import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.RequireRole
import org.genhttp.modules.authentication.getUser
import org.genhttp.modules.reflection.InterceptionResult
import org.genhttp.modules.reflection.OperationInterceptor
import org.genhttp.modules.reflection.operations.Operation

/**
 * Denies access to service methods annotated with [RequireRole] if the authenticated user does not have the
 * required roles.
 */
public class RoleInterceptor : OperationInterceptor {

    private var roles: Array<out String>? = null

    override fun configure(attribute: Annotation) {
        if (attribute is RequireRole) {
            roles = attribute.roles
        }
    }

    override suspend fun intercept(request: Request, operation: Operation, arguments: Map<ByteString, Any?>): InterceptionResult? {
        val roles = roles

        if (roles != null && roles.isNotEmpty()) {
            val user = request.getUser<User>()
                ?: throw ProviderException(ResponseStatus.Unauthorized, "Authorization required to access this endpoint")

            val userRoles = user.roles

            val missing = ArrayList<String>(roles.size)

            if (userRoles != null) {
                for (role in roles) {
                    if (userRoles.none { it.equals(role, ignoreCase = true) }) {
                        missing.add(role)
                    }
                }
            } else {
                missing.addAll(roles)
            }

            if (missing.isNotEmpty()) {
                throw ProviderException(ResponseStatus.Forbidden, "User is not authorized to access this endpoint.")
            }
        }

        return null
    }

}
