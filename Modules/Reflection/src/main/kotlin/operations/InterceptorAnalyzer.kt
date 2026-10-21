package org.genhttp.modules.reflection.operations

import org.genhttp.modules.reflection.InterceptWith
import org.genhttp.modules.reflection.OperationInterceptor

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotations

/**
 * Collects the interceptors declared for a service method.
 */
public object InterceptorAnalyzer {

    /**
     * Creates and configures the interceptors declared on the given function, either via [InterceptWith] directly
     * or via annotations whose class is annotated with [InterceptWith].
     */
    public fun getInterceptors(method: KFunction<*>): List<OperationInterceptor> {
        val interceptors = mutableListOf<OperationInterceptor>()

        // declared directly on the function (handles repeated declarations)
        for (declaration in method.findAnnotations<InterceptWith>()) {
            interceptors.add(create(declaration.interceptor, declaration))
        }

        // declared on the annotation classes of the annotations of the function
        for (annotation in method.annotations) {
            val annotationType = annotation.annotationClass.java

            if (annotationType == InterceptWith::class.java || annotationType.enclosingClass == InterceptWith::class.java) {
                continue
            }

            for (declaration in annotationType.getAnnotationsByType(InterceptWith::class.java)) {
                interceptors.add(create(declaration.interceptor, annotation))
            }
        }

        return interceptors
    }

    private fun create(type: KClass<out OperationInterceptor>, annotation: Annotation): OperationInterceptor {
        val interceptor = type.createInstance()
        interceptor.configure(annotation)
        return interceptor
    }

}
