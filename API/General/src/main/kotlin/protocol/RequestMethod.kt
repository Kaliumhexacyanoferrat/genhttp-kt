package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * Represents an HTTP request method (verb).
 */
public class RequestMethod : MemoryView {

    /**
     * Creates a request method on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates a request method on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates a request method from the given string (e.g. "GET").
     */
    public constructor(value: String) : super(value)

    /**
     * Well-known request methods.
     */
    public companion object {

        /** GET */
        public val Get: RequestMethod = RequestMethod("GET")

        /** HEAD */
        public val Head: RequestMethod = RequestMethod("HEAD")

        /** POST */
        public val Post: RequestMethod = RequestMethod("POST")

        /** PUT */
        public val Put: RequestMethod = RequestMethod("PUT")

        /** DELETE */
        public val Delete: RequestMethod = RequestMethod("DELETE")

        /** CONNECT */
        public val Connect: RequestMethod = RequestMethod("CONNECT")

        /** OPTIONS */
        public val Options: RequestMethod = RequestMethod("OPTIONS")

        /** TRACE */
        public val Trace: RequestMethod = RequestMethod("TRACE")

        /** PATCH */
        public val Patch: RequestMethod = RequestMethod("PATCH")

    }

}
