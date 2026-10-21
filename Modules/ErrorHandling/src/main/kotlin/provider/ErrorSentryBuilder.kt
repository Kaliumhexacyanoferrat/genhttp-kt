package org.genhttp.modules.errorhandling.provider

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.modules.errorhandling.ErrorMapper

import kotlin.reflect.KClass

/**
 * Creates the concern that maps exceptions to responses.
 *
 * @param T The type of the exceptions to be handled
 * @param handler The mapper used to convert the exceptions
 * @param errorType The type of the exceptions to be handled (required as generic types are erased on the JVM)
 */
public class ErrorSentryBuilder<T : Throwable>(
    private val handler: ErrorMapper<T>,
    private val errorType: KClass<T>
) : ConcernBuilder {

    override fun build(content: Handler): Concern = ErrorSentry(content, handler, errorType)

}
