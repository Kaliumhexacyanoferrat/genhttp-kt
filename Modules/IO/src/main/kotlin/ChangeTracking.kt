@file:JvmName("ChangeTracking")

package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.io.tracking.ChangeTrackingResource

/**
 * Wraps the given resource into a change tracker that can
 * tell, whether the content of the resource has changed
 * since it's content was read the last time.
 *
 * Use this mechanism for functionality that needs to
 * perform computation heavy work such as parsing
 * or interpreting the content of the resource.
 *
 * @return The given resource with change tracking attached
 */
public fun Resource.track(): ChangeTrackingResource = ChangeTrackingResource(this)

/**
 * Builds the resource and wraps it into a change tracker that can
 * tell, whether the content of the resource has changed
 * since it's content was read the last time.
 *
 * Use this mechanism for functionality that needs to
 * perform computation heavy work such as parsing
 * or interpreting the content of the resource.
 *
 * @return The newly created resource with change tracking attached
 */
public fun Builder<Resource>.buildWithTracking(): ChangeTrackingResource = ChangeTrackingResource(build())
