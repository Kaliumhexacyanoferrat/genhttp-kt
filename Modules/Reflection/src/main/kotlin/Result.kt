package org.genhttp.modules.reflection

import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.CookieOptions
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseModification
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.cookie

/**
 * Wraps the result of a service method, allowing to adjust the response sent to the client
 * (e.g. to set a status code or a header).
 *
 * @param T The type of payload
 * @property payload The result of the method to be sent to the client
 */
public open class Result<T>(override val payload: T?) : ResultWrapper, ResponseModification<Result<T>> {

    private var status: ResponseStatus? = null

    private var connectionHandling: Connection? = null

    private var headers: MutableMap<String, String>? = null

    private var cookies: MutableList<Triple<String, String, CookieOptions>>? = null

    override fun status(status: ResponseStatus): Result<T> {
        this.status = status
        return this
    }

    override fun connection(handling: Connection): Result<T> {
        connectionHandling = handling
        return this
    }

    override fun header(key: String, value: String): Result<T> {
        val map = headers ?: LinkedHashMap<String, String>().also { headers = it }
        map[key] = value
        return this
    }

    override fun cookie(name: String, value: String, options: CookieOptions): Result<T> {
        val list = cookies ?: mutableListOf<Triple<String, String, CookieOptions>>().also { cookies = it }
        list.add(Triple(name, value, options))
        return this
    }

    override fun apply(builder: ResponseBuilder) {
        status?.let { builder.status(it) }

        connectionHandling?.let { builder.connection(it) }

        headers?.forEach { (key, value) -> builder.header(key, value) }

        cookies?.forEach { (name, value, options) -> builder.cookie(name, value, options) }
    }

}
