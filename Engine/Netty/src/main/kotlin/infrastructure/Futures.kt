package org.genhttp.engine.netty.infrastructure

import io.netty.util.concurrent.Future

import kotlinx.coroutines.suspendCancellableCoroutine

import kotlin.coroutines.resume

/**
 * Suspends until the future has completed (successfully or not) and returns it.
 */
internal suspend fun <F : Future<*>> F.awaitDone(): F {
    if (!isDone) {
        suspendCancellableCoroutine { continuation ->
            addListener { continuation.resume(Unit) }
        }
    }

    return this
}
