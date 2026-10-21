package org.genhttp.api.protocol

import org.genhttp.api.infrastructure.Builder

/**
 * Allows to create and modify responses.
 */
public interface ResponseBuilder : Builder<Response> {

    /**
     * Sets the status of the HTTP response.
     *
     * @param status The HTTP status code to respond with
     * @return The builder instance
     */
    public fun status(status: ResponseStatus): ResponseBuilder

    /**
     * Configures how the server should treat the client connection.
     *
     * @param mode The mode the server should apply
     * @return The builder instance
     */
    public fun connection(mode: Connection): ResponseBuilder

    /**
     * Sets a HTTP header on the response.
     *
     * @param name The name of the header to be set
     * @param value The value of the header to be set
     * @return The builder instance
     */
    public fun header(name: ByteString, value: ByteString): ResponseBuilder

    /**
     * Sets a HTTP header on the response.
     *
     * @param name The name of the header to be set
     * @param value The value of the header to be set
     * @return The builder instance
     */
    public fun header(name: String, value: String): ResponseBuilder

    /**
     * Sets the content of the HTTP response.
     *
     * @param content The content to be set
     * @return The builder instance
     */
    public fun content(content: ResponseContent?): ResponseBuilder

}
