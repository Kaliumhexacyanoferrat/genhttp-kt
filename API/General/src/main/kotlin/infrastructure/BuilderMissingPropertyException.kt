package org.genhttp.api.infrastructure

/**
 * Will be thrown, if a builder is missing a required property that is needed to create the target instance.
 *
 * @property property The name of the property which has not been set
 */
public class BuilderMissingPropertyException(public val property: String) :
    RuntimeException("Missing required property '$property'")
