package org.genhttp.modules.openapi.discovery

import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.protocol.RequestMethod

/**
 * The concerns that wrap the handler currently explored and are able
 * to document their functionality (see [DocumentedConcern]).
 *
 * Immutable, so explorers can pass an extended instance to the content of
 * a concern without affecting the siblings of the concern.
 */
public class InheritedDocumentation private constructor(
    private val outer: InheritedDocumentation?,
    private val concern: DocumentedConcern?
) {

    // region Get-/Setters

    /**
     * Provides the instance used to start exploring.
     */
    public companion object {

        /**
         * An instance without any documentation, used to start exploring.
         */
        public val Empty: InheritedDocumentation = InheritedDocumentation(null, null)

    }

    // endregion

    // region Functionality

    /**
     * Creates the documentation to be applied to the content of the given concern.
     *
     * @param concern The concern whose content is about to be explored
     * @return The documentation to explore the content of the concern with
     */
    public fun push(concern: DocumentedConcern): InheritedDocumentation = InheritedDocumentation(this, concern)

    /**
     * Asks the concerns to document the given operation.
     *
     * @param method The HTTP verb of the operation to be documented
     * @return The documentation added by the individual concerns, starting with the innermost one
     */
    public fun getDocumentation(method: RequestMethod): List<OperationDocumentation> {
        val result = mutableListOf<OperationDocumentation>()

        var current: InheritedDocumentation = this

        while (true) {
            val concern = current.concern ?: break

            val operation = OperationDocumentation(method)

            concern.addDocumentation(operation)

            if (operation.entries.isNotEmpty()) {
                result.add(operation)
            }

            current = current.outer!!
        }

        return result
    }

    // endregion

}
