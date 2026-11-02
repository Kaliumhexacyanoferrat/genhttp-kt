package org.genhttp.modules.reflection

import org.genhttp.api.content.documentation.OperationDocumentation

/**
 * An interceptor that documents the functionality it adds to the operation it has been placed on (such as the
 * responses it may generate), so it can be presented to the consumers of the API.
 *
 * Responses and headers added by the interceptor take precedence over the documentation declared on the service
 * class or by concerns, but not over the documentation declared on the method itself.
 */
public interface DocumentedInterceptor : OperationInterceptor {

    /**
     * Invoked for every HTTP verb supported by the operation, allowing the interceptor to document the
     * functionality it adds to the operation.
     *
     * @param operation The operation to add the documentation to
     */
    public fun addDocumentation(operation: OperationDocumentation)

}
