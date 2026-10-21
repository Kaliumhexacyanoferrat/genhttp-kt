package org.genhttp.api.protocol

/**
 * A HTTP response to be sent to a connected client.
 */
public interface Response {

    /**
     * The status of the HTTP response.
     */
    public val status: ResponseStatus

    /**
     * How the server should proceed with the client connection.
     */
    public val mode: Connection

    /**
     * The HTTP headers to be sent to the client.
     */
    public val headers: KeyValueList

    /**
     * The content of the response, if any.
     */
    public val content: ResponseContent?

    /**
     * Call to get access to the builder which allows to modify the response as needed.
     *
     * @return The response builder which can be used to modify the response
     */
    public fun rebuild(): ResponseBuilder

}
