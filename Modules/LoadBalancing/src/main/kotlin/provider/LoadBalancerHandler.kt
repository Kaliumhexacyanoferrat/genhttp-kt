package org.genhttp.modules.loadbalancing.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.loadbalancing.PriorityEvaluation

import kotlin.random.Random

/**
 * A handler that distributes requests to a set of nodes. The nodes sharing the highest
 * priority for a request are selected, one of them is picked randomly to handle it.
 *
 * @param nodes The nodes to be balanced, with their priority evaluation
 */
public class LoadBalancerHandler(nodes: List<Pair<HandlerBuilder, PriorityEvaluation>>) : Handler {

    private val nodes: List<Pair<Handler, PriorityEvaluation>> = nodes.map { Pair(it.first.build(), it.second) }

    // region Functionality

    override suspend fun prepare(server: Server) {
        for (entry in nodes) {
            entry.first.prepare(server)
        }
    }

    override suspend fun handle(request: Request): Response? {
        // get the handlers that share the highest priority
        val priorityGroup = nodes.groupBy { it.second(request) }
            .maxByOrNull { it.key.value }
            ?.value
            ?.map { it.first }

        if (priorityGroup != null) {
            // let a random one handle the request
            if (priorityGroup.size > 1) {
                val index = Random.nextInt(0, priorityGroup.size)

                return priorityGroup[index].handle(request)
            }

            return priorityGroup[0].handle(request)
        }

        return null
    }

    // endregion

}
