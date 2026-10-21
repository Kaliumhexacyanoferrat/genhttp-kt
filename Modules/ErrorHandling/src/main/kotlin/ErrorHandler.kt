package org.genhttp.modules.errorhandling

import org.genhttp.modules.conversion.serializers.SerializationBuilder
import org.genhttp.modules.errorhandling.mappers.HtmlErrorMapper
import org.genhttp.modules.errorhandling.mappers.StructuredErrorMapper
import org.genhttp.modules.errorhandling.provider.ErrorSentryBuilder

import kotlin.reflect.KClass

/**
 * Factory for the concerns that convert exceptions into HTTP responses.
 */
public object ErrorHandler {

    /**
     * The default error handler used by the server to
     * render error pages.
     *
     * By default, server errors will be rendered into
     * structured responses.
     *
     * @return The default error handler
     */
    public fun default(): ErrorSentryBuilder<Exception> = structured()

    /**
     * An error handler which will render exceptions into
     * structured error objects serialized to the format
     * requested by the client (e.g. JSON or XML).
     *
     * @param serialization The serialization configuration to be used
     * @return A structured error handler
     */
    public fun structured(serialization: SerializationBuilder? = null): ErrorSentryBuilder<Exception> =
        from(StructuredErrorMapper(serialization?.build()))

    /**
     * An error handler which will render exceptions into
     * HTML using the current template and the server renderer.
     *
     * @return An HTML error handler
     */
    public fun html(): ErrorSentryBuilder<Exception> = from(HtmlErrorMapper())

    /**
     * Creates an error handling concern which will use
     * the specified error mapper to convert exceptions
     * to HTTP responses.
     *
     * @param T The type of exceptions to be catched
     * @param mapper The mapper to use for exception mapping
     * @return The newly generated concern
     */
    public inline fun <reified T : Throwable> from(mapper: ErrorMapper<T>): ErrorSentryBuilder<T> =
        from(mapper, T::class)

    /**
     * Creates an error handling concern which will use
     * the specified error mapper to convert exceptions
     * to HTTP responses.
     *
     * This overload is required as generic types are erased on the JVM: [from] determines the type
     * of the exceptions to be catched automatically.
     *
     * @param T The type of exceptions to be catched
     * @param mapper The mapper to use for exception mapping
     * @param errorType The type of exceptions to be catched
     * @return The newly generated concern
     */
    public fun <T : Throwable> from(mapper: ErrorMapper<T>, errorType: KClass<T>): ErrorSentryBuilder<T> =
        ErrorSentryBuilder(mapper, errorType)

}
