package org.genhttp.api.protocol

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The input of an upgraded connection that can be awaited without blocking a thread.
 *
 * Engines that serve their connections on event loops (such as the Netty engine) return a stream implementing this
 * interface from [Request.upgrade]. Content that wants to read the upgraded connection without blocking switches to
 * the [dispatcher] of the input (`withContext(input.dispatcher)`) and reads with [readSuspending]. The blocking
 * methods of the stream keep working on other threads (e.g. virtual threads). Engines that serve their connections
 * on threads of their own (such as the internal engine) return a plain stream.
 *
 * This interface is not part of GenHTTP, which reads upgraded connections through a `PipeReader` that can be awaited
 * anyway.
 */
public interface SuspendingInput {

    /**
     * The dispatcher of the thread that receives the data of the connection. Content reading the input without
     * blocking runs on it.
     */
    public val dispatcher: CoroutineDispatcher

    /**
     * Reads up to `length` bytes into `target`, suspending until data is available.
     *
     * @param target The array to read the data into
     * @param offset The position in the array the data is written to
     * @param length The maximum number of bytes to be read
     * @return The number of bytes read, or -1 at the end of the input
     */
    public suspend fun readSuspending(target: ByteArray, offset: Int, length: Int): Int

}
