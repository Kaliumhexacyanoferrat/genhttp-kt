@file:JvmName("Extensions")

package org.genhttp.modules.apibrowsing

import org.genhttp.modules.apibrowsing.common.BrowserHandlerBuilder
import org.genhttp.modules.layouting.provider.LayoutBuilder

/**
 * Creates a Swagger UI application and registers it at the layout.
 *
 * There is no auto-detection of Open API definitions provided by the server
 * so the URL provided needs to point to the correct definition to be consumed.
 * Use relative paths to avoid issues with CORS, proxies etc.
 *
 * @receiver The layout to add the application to
 * @param segment The path to make the application available from (defaults to "/swagger/")
 * @param url The URL of the Open API definition to be rendered (defaults to "../openapi.json")
 * @param title The title of the rendered application
 * @return The layout once again (builder pattern)
 */
public fun LayoutBuilder.addSwaggerUi(segment: String = "swagger", url: String? = null, title: String? = null): LayoutBuilder =
    addBrowser(ApiBrowser.swaggerUi(), segment, url, title)

/**
 * Creates a Redoc application and registers it at the layout.
 *
 * There is no auto-detection of Open API definitions provided by the server
 * so the URL provided needs to point to the correct definition to be consumed.
 * Use relative paths to avoid issues with CORS, proxies etc.
 *
 * @receiver The layout to add the application to
 * @param segment The path to make the application available from (defaults to "/redoc/")
 * @param url The URL of the Open API definition to be rendered (defaults to "../openapi.json")
 * @param title The title of the rendered application
 * @return The layout once again (builder pattern)
 */
public fun LayoutBuilder.addRedoc(segment: String = "redoc", url: String? = null, title: String? = null): LayoutBuilder =
    addBrowser(ApiBrowser.redoc(), segment, url, title)

/**
 * Creates a Scalar application and registers it at the layout.
 *
 * There is no auto-detection of Open API definitions provided by the server
 * so the URL provided needs to point to the correct definition to be consumed.
 * Use relative paths to avoid issues with CORS, proxies etc.
 *
 * @receiver The layout to add the application to
 * @param segment The path to make the application available from (defaults to "/scalar/")
 * @param url The URL of the Open API definition to be rendered (defaults to "../openapi.json")
 * @param title The title of the rendered application
 * @return The layout once again (builder pattern)
 */
public fun LayoutBuilder.addScalar(segment: String = "scalar", url: String? = null, title: String? = null): LayoutBuilder =
    addBrowser(ApiBrowser.scalar(), segment, url, title)

private fun LayoutBuilder.addBrowser(builder: BrowserHandlerBuilder, segment: String, url: String?, title: String?): LayoutBuilder {
    if (url != null) {
        builder.url(url)
    }

    if (title != null) {
        builder.title(title)
    }

    return add(segment, builder)
}
