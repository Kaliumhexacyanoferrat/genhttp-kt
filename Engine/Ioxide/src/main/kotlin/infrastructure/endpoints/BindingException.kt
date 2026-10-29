package org.genhttp.engine.ioxide.infrastructure.endpoints

/**
 * Thrown if the server is not able to bind to a configured endpoint (e.g. because the port is already in use).
 */
internal class BindingException(message: String, cause: Throwable) : RuntimeException(message, cause)
