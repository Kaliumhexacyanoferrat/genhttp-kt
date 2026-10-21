package org.genhttp.api.infrastructure

import java.io.IOException

/**
 * Will be thrown when writing a response to a client that has already closed the connection.
 *
 * Derives from [IOException], as this is what a stream throws if its connection is gone and what content written
 * to one already handles. It has a type of its own so that engines can tell a client that went away - which is
 * nothing to report - from content that failed.
 */
public class ConnectionClosedException : IOException("The client closed the connection")
