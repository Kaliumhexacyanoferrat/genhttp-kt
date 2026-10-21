package org.genhttp.engine.shared.types.body

/**
 * The way a request body is consumed by the handler.
 */
internal enum class ConsumptionStrategy {

    Stream,

    Memory

}
