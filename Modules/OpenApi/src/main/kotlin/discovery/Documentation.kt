package org.genhttp.modules.openapi.discovery

import org.genhttp.api.content.services.Remarks
import org.genhttp.api.content.services.Summary

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Reads the documentation annotations (such as [Summary])
 * declared on services and their models.
 */
internal object Documentation {

    fun getSummary(member: KAnnotatedElement): String? = find<Summary>(member)?.text

    fun getSummary(parameter: KParameter): String? = parameter.findAnnotation<Summary>()?.text

    /**
     * Reads the summary placed on the type returned by the function (`fun get(): @Summary("...") Item`).
     */
    fun getResultSummary(function: KFunction<*>): String? =
        function.returnType.annotations.filterIsInstance<Summary>().firstOrNull()?.text

    fun getRemarks(member: KAnnotatedElement): String? = find<Remarks>(member)?.text

    /**
     * Combines summary and remarks of the given member into a single text,
     * for targets that do not distinguish between them.
     */
    fun getDescription(member: KAnnotatedElement): String? = combine(getSummary(member), getRemarks(member))

    fun combine(summary: String?, remarks: String?): String? {
        if (summary == null) {
            return remarks
        }

        return if (remarks == null) summary else "$summary\n\n$remarks"
    }

    /**
     * Finds the annotation on the given element. For functions, the declarations the function overrides
     * are searched as well (as `GetCustomAttribute(inherit: true)` does).
     */
    private inline fun <reified T : Annotation> find(member: KAnnotatedElement): T? {
        member.findAnnotation<T>()?.let { return it }

        if (member is KFunction<*>) {
            return overridden(member).firstNotNullOfOrNull { it.findAnnotation<T>() }
        }

        return null
    }

    /**
     * Finds the functions overridden by the given function, starting with the closest one.
     */
    fun overridden(function: KFunction<*>): Sequence<KFunction<*>> {
        val method = runCatching { function.javaMethod }.getOrNull() ?: return emptySequence()

        return generateSequence(method.declaringClass.superclass) { it.superclass }
            .mapNotNull { type -> runCatching { type.getDeclaredMethod(method.name, *method.parameterTypes) }.getOrNull() }
            .mapNotNull { runCatching { it.kotlinFunction }.getOrNull() }
    }

}
