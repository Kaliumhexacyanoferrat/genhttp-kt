package org.genhttp.modules.loadbalancing

import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.Request

/**
 * Determines the priority of a node of the load balancer for the given request.
 * The nodes with the highest priority are used to handle the request.
 */
public typealias PriorityEvaluation = (request: Request) -> Priority
