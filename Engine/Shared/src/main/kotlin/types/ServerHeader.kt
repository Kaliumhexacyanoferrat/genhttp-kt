package org.genhttp.engine.shared.types

/**
 * Provides the rendered `Server` header line.
 */
internal object ServerHeader {

    @Volatile
    private var cached: ByteArray? = null

    fun getValue(context: ClientContext): ByteArray =
        cached ?: "Server: GenHTTP/${context.server.version}\r\n".toByteArray(Charsets.US_ASCII).also { cached = it }

}
