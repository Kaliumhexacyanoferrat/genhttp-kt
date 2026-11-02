package org.genhttp.modules.reflection.operations

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

/**
 * Analyzes the types that provide an API (such as webservices or controllers).
 */
public object TypeAnalyzer {

    /**
     * Finds the public surface of the given API type: the member functions declared by the type
     * itself that are `public` or `internal`.
     *
     * @param type The type to analyze
     * @return The public API surface of the given type
     */
    public fun findMethods(type: KClass<*>): List<KFunction<*>> =
        type.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC || it.visibility == KVisibility.INTERNAL }

}
