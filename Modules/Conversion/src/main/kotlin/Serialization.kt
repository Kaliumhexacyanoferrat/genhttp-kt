package org.genhttp.modules.conversion

import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.conversion.serializers.SerializationBuilder
import org.genhttp.modules.conversion.serializers.forms.FormFormat
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.conversion.serializers.xml.XmlFormat
import org.genhttp.modules.conversion.serializers.yaml.YamlFormat

import tools.jackson.databind.json.JsonMapper

/**
 * Entry point to configure the formats supported by a webservice
 * resource.
 */
public object Serialization {

    /**
     * Returns a registry that will support JSON, XML, YAML and form encoded data
     * and will use JSON as a default format.
     *
     * @param jsonMapper The mapper to be used for JSON (defaults to a mapper built from [JsonFormat.getDefaultOptions])
     */
    public fun default(jsonMapper: JsonMapper? = null): SerializationBuilder {
        val xmlFormat = XmlFormat()

        return SerializationBuilder().default(ContentType.ApplicationJson)
            .add(ContentType.ApplicationJson, JsonFormat(jsonMapper))
            .add(ContentType.ApplicationYaml, YamlFormat())
            .add(ContentType.ApplicationWwwFormUrlEncoded, FormFormat())
            .add(ContentType.ApplicationXml, xmlFormat)
            .add(ContentType.TextXml, xmlFormat)
    }

    /**
     * Returns an empty registry to be customized.
     */
    public fun empty(): SerializationBuilder = SerializationBuilder()

}
