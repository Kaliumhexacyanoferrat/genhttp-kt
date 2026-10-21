package org.genhttp.modules.controllers

import org.genhttp.modules.reflection.Method
import org.genhttp.modules.reflection.MethodAttribute

/**
 * Specifies the request methods an action of a controller handles (by default, actions handle GET and HEAD).
 *
 * @property methods The request methods handled by the action
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class ControllerAction(vararg val methods: Method)

/**
 * The configuration of an action declared via [ControllerAction] (`ControllerActionAttribute` in GenHTTP).
 */
public class ControllerActionConfiguration(annotation: ControllerAction) : MethodAttribute(*annotation.methods)
