package org.genhttp.engine.shared.infrastructure.logging

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

import org.slf4j.Logger

import java.util.Locale

/**
 * Logs every handled request with status, length and duration.
 */
public class RequestLoggingConcern(override val content: Handler) : Concern {

    private var logger: Logger? = null

    override suspend fun prepare(server: Server) {
        logger = server.logging.getLogger("GenHTTP.Requests")
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val logger = logger

        if (logger != null && logger.isInfoEnabled) {
            return handleAndLog(request, logger)
        }

        return content.handle(request)
    }

    private suspend fun handleAndLog(request: Request, logger: Logger): Response? {
        val header = request.header

        val method = header.method.toString()
        val path = header.path.toString()

        val start = System.nanoTime()

        val response = content.handle(request)

        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        val status = response?.status?.code ?: 404
        val length = response?.content?.length ?: 0

        logger.info("{} {} - HTTP {} - {} bytes - {} ms", method, path, status, length, String.format(Locale.ROOT, "%.2f", elapsed))

        return response
    }

}
