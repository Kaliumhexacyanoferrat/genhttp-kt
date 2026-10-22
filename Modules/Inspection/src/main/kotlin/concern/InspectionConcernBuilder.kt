package org.genhttp.modules.inspection.concern

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.conversion.Serialization as Conv
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.conversion.serializers.yaml.YamlFormat

/**
 * Configures the concern that provides information about requests and responses.
 */
public class InspectionConcernBuilder : ConcernBuilder {

    private var serialization: SerializationRegistry? = null

    // region Functionality

    /**
     * Sets the registry that is used to serialize the information about the request.
     *
     * @param registry The registry to be used (JSON and YAML, if not set)
     */
    public fun serialization(registry: SerializationRegistry): InspectionConcernBuilder {
        serialization = registry
        return this
    }

    override fun build(content: Handler): Concern {
        val serialization = serialization ?: Conv.empty()
            .default(ContentType.ApplicationJson)
            .add(ContentType.ApplicationJson, JsonFormat())
            .add(ContentType.ApplicationYaml, YamlFormat())
            .build()

        return InspectionConcern(content, serialization)
    }

    // endregion

}
