package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * The settings used to execute the methods of a service.
 *
 * @property mode The execution mode to be used (defaults to [ExecutionMode.Auto])
 */
public data class ExecutionSettings(val mode: ExecutionMode?)
