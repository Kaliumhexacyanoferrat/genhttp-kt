package org.genhttp.modules.inspection.concern

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ClientProtocol
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.KeyValueList
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.containsKey
import org.genhttp.api.protocol.getEntry
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.api.protocol.preAllocate
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.io.strings.StringContent

/**
 * Serializes information about the server, the request and the response, if the `inspect` query
 * parameter has been added to the request.
 *
 * @param content The handler to be wrapped by this concern
 * @param serialization The registry used to serialize the information
 */
public class InspectionConcern(override val content: Handler, public val serialization: SerializationRegistry) : Concern {

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        if (request.header.query.containsKey(InspectInstruction)) {
            val accepted = request.header.headers.getEntry(KnownHeaders.Accept).preAllocate(request)

            val response = content.handle(request)

            val server = request.server

            val header = request.header

            val target = header.target

            val responseContent = response?.content

            val model = InspectionModel(
                server = ServerModel(
                    version = server.version,
                    development = server.development,
                    handler = server.handler.toString(),
                    endpoints = server.endPoints.map {
                        EndPointModel(
                            port = it.port,
                            ipAddress = it.address?.hostAddress,
                            secure = it.secure,
                            requestSource = it == request.endPoint
                        )
                    }
                ),
                client = ClientModel(
                    protocol = request.client.protocol,
                    ipAddress = request.client.address?.hostAddress
                ),
                request = RequestModel(
                    protocol = header.protocol.toString(),
                    method = header.method.toString(),
                    path = header.path.toString(),
                    target = TargetModel(
                        current = target.current?.toString(),
                        trailingSlash = target.hasTrailingSlash,
                        last = target.isLast
                    ),
                    headers = getList(header.headers),
                    query = getList(header.query)
                ),
                response = if (response != null) ResponseModel(
                    status = response.status,
                    mode = response.mode,
                    headers = getList(response.headers),
                    content = if (responseContent != null) ContentModel(
                        length = responseContent.length,
                        type = responseContent.type?.toString(),
                        checksum = responseContent.calculateChecksum()
                    ) else null
                ) else null
            )

            val format = serialization.getSerialization(request, accepted)
                ?: return request.respond()
                    .status(ResponseStatus.UnsupportedMediaType)
                    .content(StringContent("Unable to find serializer for requested format"))
                    .build()

            return format.serialize(request, model).build()
        }

        return content.handle(request)
    }

    private fun getList(list: KeyValueList): List<KeyValueModel> {
        val result = ArrayList<KeyValueModel>(list.count)

        for (i in 0 until list.count) {
            val (key, value) = list.getStringEntry(i)

            result.add(KeyValueModel(key.toString(), value.toString()))
        }

        return result
    }

    // endregion

    private companion object {

        private val InspectInstruction = ByteString("inspect")

    }

}

// region Model (serialized into the response)

internal class InspectionModel(
    val server: ServerModel,
    val client: ClientModel,
    val request: RequestModel,
    val response: ResponseModel?
)

internal class ServerModel(
    val version: String,
    val development: Boolean,
    val handler: String,
    val endpoints: List<EndPointModel>
)

internal class EndPointModel(
    val port: Int,
    val ipAddress: String?,
    val secure: Boolean,
    val requestSource: Boolean
)

internal class ClientModel(
    val protocol: ClientProtocol?,
    val ipAddress: String?
)

internal class RequestModel(
    val protocol: String,
    val method: String,
    val path: String,
    val target: TargetModel,
    val headers: List<KeyValueModel>,
    val query: List<KeyValueModel>
)

internal class TargetModel(
    val current: String?,
    val trailingSlash: Boolean,
    val last: Boolean
)

internal class ResponseModel(
    val status: ResponseStatus,
    val mode: Connection,
    val headers: List<KeyValueModel>,
    val content: ContentModel?
)

internal class ContentModel(
    val length: Long?,
    val type: String?,
    val checksum: Long?
)

internal class KeyValueModel(
    val key: String,
    val value: String
)

// endregion
