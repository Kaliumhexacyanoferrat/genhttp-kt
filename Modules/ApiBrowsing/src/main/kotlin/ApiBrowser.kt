package org.genhttp.modules.apibrowsing

import org.genhttp.modules.apibrowsing.common.BrowserHandlerBuilder

/**
 * Provides graphical, JavaScript based web applications that render an Open API
 * definition so that the API can be explored by users.
 */
public object ApiBrowser {

    /**
     * Creates a handler that will provide a Swagger UI app.
     *
     * @return The newly created handler
     */
    public fun swaggerUi(): BrowserHandlerBuilder = BrowserHandlerBuilder("Swagger", "Swagger UI")

    /**
     * Creates a handler that will provide a Redoc app.
     *
     * @return The newly created handler
     */
    public fun redoc(): BrowserHandlerBuilder = BrowserHandlerBuilder("Redoc", "Redoc")

    /**
     * Creates a handler that will provide a Scalar app.
     *
     * @return The newly created handler
     */
    public fun scalar(): BrowserHandlerBuilder = BrowserHandlerBuilder("Scalar", "Scalar")

}
