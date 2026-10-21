package org.genhttp.modules.io.streaming

/**
 * Centralizes checksum handling of binary data by providing a cache.
 *
 * @param provider The checksum provider to invoke
 */
public class ChecksumProvider(private val provider: suspend () -> Long?) {

    @Volatile
    private var checksumComputed = false

    @Volatile
    private var cachedChecksum: Long? = null

    /**
     * Returns the cached checksum or invokes the underlying provider (once) to compute it.
     *
     * @return The checksum computed by the provider, if any
     */
    public suspend fun compute(): Long? {
        if (checksumComputed) {
            return cachedChecksum
        }

        val checksum = provider()

        cachedChecksum = checksum
        checksumComputed = true

        return checksum
    }

}
