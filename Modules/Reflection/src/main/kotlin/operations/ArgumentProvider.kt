package org.genhttp.modules.reflection.operations

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.PercentEncoding
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.conversion.convertTo
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.routing.RoutingMatch

import java.io.InputStream

import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Reads the arguments of operations from requests.
 */
public object ArgumentProvider {

    /**
     * Provides an argument using the injectors of the registry.
     */
    public suspend fun getInjectedArgument(request: Request, handler: Handler, argument: OperationArgument, registry: MethodRegistry): Any? {
        val type = argument.type.classifier as? KClass<*> ?: return null

        for (injector in registry.injection) {
            if (injector.supports(request.server, type)) {
                return injector.getValue(handler, request, type)
            }
        }

        return null
    }

    /**
     * Reads an argument from the path.
     */
    public fun getPathArgument(name: ByteString, type: KType, match: RoutingMatch, registry: MethodRegistry): Any? {
        val value = match.pathArguments?.get(name) ?: return null
        return value.convertTo(type, registry.formatting)
    }

    /**
     * Reads a plain argument from the request body.
     */
    public suspend fun getBodyArgument(request: Request, name: ByteString, type: KType, registry: MethodRegistry): Any? {
        val content = request.getBody(HeaderAccess.Release)
            ?: throw ProviderException(ResponseStatus.BadRequest, "Argument '$name' is expected to be read from the request body but the request does not contain any payload")

        val buffer = ByteString(content.asMemory())

        if (buffer.isEmpty) {
            return null
        }

        return buffer.convertTo(type, registry.formatting)
    }

    /**
     * Reads an argument from the query.
     */
    public fun getQueryArgument(request: Request, argument: OperationArgument, registry: MethodRegistry): Any? {
        val queryValue = getQueryValue(request, argument.name)

        // an empty value falls back to the default value of the parameter, if any
        if (queryValue != null && (!argument.isOptional || !queryValue.isEmpty)) {
            return queryValue.convertTo(argument.type, registry.formatting)
        }

        return null
    }

    /**
     * Reads and decodes the query value with the given name.
     */
    public fun getQueryValue(request: Request, name: ByteString): ByteString? {
        val queryValue = request.header.query.getEntry(name) ?: return null
        return ByteString(PercentEncoding.decode(queryValue.bytes, decodePlus = true))
    }

    /**
     * Deserializes an argument from the request body.
     */
    public suspend fun getContent(request: Request, argument: OperationArgument, registry: MethodRegistry): Any? {
        val deserializer = registry.serialization.getDeserialization(request)
            ?: throw ProviderException(ResponseStatus.UnsupportedMediaType, "Requested format is not supported")

        val content = request.getBody(HeaderAccess.Release)
            ?: throw ProviderException(ResponseStatus.BadRequest, "Request body expected")

        try {
            return deserializer.deserialize(content.asStream(), argument.type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ProviderException(ResponseStatus.BadRequest, "Failed to deserialize request body", e)
        }
    }

    /**
     * Provides the request body as a stream.
     */
    public fun getStream(request: Request): InputStream {
        val content = request.getBody(HeaderAccess.Release)
            ?: throw ProviderException(ResponseStatus.BadRequest, "Request body expected")

        return content.asStream()
    }

}
