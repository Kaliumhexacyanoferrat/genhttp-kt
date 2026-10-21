package org.genhttp.api.protocol

/**
 * Header names that are shared across several handlers and concerns.
 */
public object KnownHeaders {

    /** Connection */
    public val Connection: ByteString = ByteString("Connection")

    /** Server */
    public val Server: ByteString = ByteString("Server")

    /** Date */
    public val Date: ByteString = ByteString("Date")

    /** Host */
    public val Host: ByteString = ByteString("Host")

    /** Vary */
    public val Vary: ByteString = ByteString("Vary")

    /** Content-Type */
    public val ContentType: ByteString = ByteString("Content-Type")

    /** Content-Encoding */
    public val ContentEncoding: ByteString = ByteString("Content-Encoding")

    /** Content-Length */
    public val ContentLength: ByteString = ByteString("Content-Length")

    /** Transfer-Encoding */
    public val TransferEncoding: ByteString = ByteString("Transfer-Encoding")

    /** Accept */
    public val Accept: ByteString = ByteString("Accept")

    /** Accept-Encoding */
    public val AcceptEncoding: ByteString = ByteString("Accept-Encoding")

    /** Expires */
    public val Expires: ByteString = ByteString("Expires")

    /** If-None-Match */
    public val IfNoneMatch: ByteString = ByteString("If-None-Match")

    /** ETag */
    public val ETag: ByteString = ByteString("ETag")

    /** Cookie */
    public val Cookie: ByteString = ByteString("Cookie")

    /** Set-Cookie */
    public val SetCookie: ByteString = ByteString("Set-Cookie")

    /** Forwarded */
    public val Forwarded: ByteString = ByteString("Forwarded")

    /** X-Forwarded-For */
    public val ForwardedFor: ByteString = ByteString("X-Forwarded-For")

    /** X-Forwarded-Host */
    public val ForwardedHost: ByteString = ByteString("X-Forwarded-Host")

    /** X-Forwarded-Proto */
    public val ForwardedProto: ByteString = ByteString("X-Forwarded-Proto")

}
