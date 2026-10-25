package org.genhttp.engine.ioxide.infrastructure

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle
import java.net.InetAddress

/**
 * The socket calls of the engine that ioxide does not offer itself (like the `LibraryImport`s of the ioxide engine
 * in GenHTTP): the address of the peer, the data a socket still has to deliver and closing a connection the server
 * stops serving.
 */
internal object Sockets {

    private const val AF_INET = 2

    private const val AF_INET6 = 10

    /**
     * Shuts both directions of a socket down.
     */
    const val SHUT_RDWR: Int = 2

    private val linker = Linker.nativeLinker()

    private val GETPEERNAME: MethodHandle = bind("getpeername", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS))

    private val SHUTDOWN: MethodHandle = bind("shutdown", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT))

    // int ioctl(int fd, unsigned long request, ...)
    private val IOCTL: MethodHandle = bind("ioctl", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS), Linker.Option.firstVariadicArg(2))

    // the bytes in the send queue of a socket that have not been acknowledged by the peer yet (SIOCOUTQ)
    private const val TIOCOUTQ = 0x5411L

    /**
     * The address of the peer connected to the given socket, or null if it cannot be determined.
     *
     * IPv4 clients of dual-stack sockets are reported with their IPv4 address.
     */
    fun peerAddress(fd: Int): InetAddress? = Arena.ofConfined().use { arena ->
        // sockaddr_storage
        val address = arena.allocate(128)
        val length = arena.allocate(JAVA_INT)

        length.set(JAVA_INT, 0, 128)

        if (GETPEERNAME.invokeExact(fd, address, length) as Int != 0) {
            return null
        }

        when (address.get(JAVA_SHORT, 0).toInt() and 0xFFFF) {
            AF_INET -> InetAddress.getByAddress(address.asSlice(4, 4).toArray(JAVA_BYTE))
            AF_INET6 -> InetAddress.getByAddress(address.asSlice(8, 16).toArray(JAVA_BYTE))
            else -> null
        }
    }

    /**
     * The number of bytes the given socket has not delivered to its peer yet, or -1 if it cannot be determined.
     */
    fun unsent(fd: Int): Int = Arena.ofConfined().use { arena ->
        val count = arena.allocate(JAVA_INT)

        if (IOCTL.invokeExact(fd, TIOCOUTQ, count) as Int != 0) {
            return -1
        }

        count.get(JAVA_INT, 0)
    }

    /**
     * Shuts the given socket down, which completes the pending receive of the connection.
     */
    fun shutdown(fd: Int, how: Int): Int = SHUTDOWN.invokeExact(fd, how) as Int

    private fun bind(name: String, descriptor: FunctionDescriptor, vararg options: Linker.Option): MethodHandle =
        linker.downcallHandle(linker.defaultLookup().find(name).orElseThrow { UnsatisfiedLinkError("$name is not available") }, descriptor, *options)

}
