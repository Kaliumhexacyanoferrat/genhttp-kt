package org.genhttp.modules.reflection

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.modules.reflection.operations.Operation

import kotlin.reflect.KClass

/**
 * The result of an interceptor that decided to handle a request on its own.
 *
 * @param payload The payload to be sent to the client (serialized like the result of a service method)
 */
public class InterceptionResult(payload: Any? = null) : Result<Any?>(payload)

/**
 * Allows to intercept the invocation of service methods (e.g. to check permissions).
 */
public interface OperationInterceptor {

    /**
     * Configures the interceptor with the annotation it has been declared with.
     *
     * @param attribute The annotation found on the service method
     */
    public fun configure(attribute: Annotation)

    /**
     * Invoked before the service method is executed.
     *
     * @param request The request to be handled
     * @param operation The operation about to be invoked
     * @param arguments The arguments the method will be invoked with
     * @return A result to be sent to the client instead of invoking the method, or null to continue
     */
    public suspend fun intercept(request: Request, operation: Operation, arguments: Map<ByteString, Any?>): InterceptionResult?

}
