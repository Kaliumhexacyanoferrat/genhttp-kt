@file:JvmName("RequestBodyExtensions")

package org.genhttp.modules.io

import org.genhttp.api.protocol.RequestBody

/**
 * Reads and parses the body as form encoded ("application/x-www-form-urlencoded") content.
 *
 * @receiver The body to be parsed
 * @return The arguments found within the body
 */
public suspend fun RequestBody.asBodyArguments(): BodyArguments = BodyArguments.create(this)
