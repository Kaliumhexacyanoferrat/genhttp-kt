package org.genhttp.modules.apibrowsing.common

/**
 * The configuration of an API browser application.
 *
 * @property url The URL of the Open API definition to be consumed (defaults to "../openapi.json")
 * @property title The title of the application rendered by the browser
 */
public data class BrowserMetaData(val url: String?, val title: String)
