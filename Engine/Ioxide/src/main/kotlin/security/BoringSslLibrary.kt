package org.genhttp.engine.ioxide.security

import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import java.nio.file.Path

/**
 * The native library ioxide terminates TLS with (`libioxide_boringssl.so`), looked up the way ioxide loads it: via
 * the `ioxide.boringssl` system property or the `IOXIDE_BORINGSSL` environment variable (each a path), otherwise by
 * name on the search path of the loader.
 */
internal object BoringSslLibrary {

    /**
     * Why the library is not available, or null if it is.
     */
    val unavailabilityCause: Throwable? by lazy { probe() }

    /**
     * Whether the library can be loaded.
     */
    val available: Boolean
        get() = unavailabilityCause == null

    private fun probe(): Throwable? {
        val path = System.getProperty("ioxide.boringssl") ?: System.getenv("IOXIDE_BORINGSSL")

        return try {
            Arena.ofConfined().use { arena ->
                val library = if (path != null) SymbolLookup.libraryLookup(Path.of(path), arena) else SymbolLookup.libraryLookup("libioxide_boringssl.so", arena)

                if (library.find("SSL_CTX_new").isEmpty) {
                    UnsatisfiedLinkError("SSL_CTX_new is not in libioxide_boringssl.so")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            UnsatisfiedLinkError("libioxide_boringssl.so could not be loaded (${path ?: "searched by name"}): ${e.message}").apply { initCause(e) }
        }
    }

}
