package org.genhttp.testing.acceptance

import org.genhttp.modules.reflection.ExecutionMode

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations

import java.util.stream.Stream

/**
 * When placed on a test function, injects both the engine as well as the execution mode to be used
 * (as parameters of type [org.genhttp.api.infrastructure.ServerEngine] and [ExecutionMode]).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest(name = "{displayName} ({0}, {1})")
@ArgumentsSource(EngineFrameworkProvider::class)
annotation class MultiEngineFrameworkTest

/**
 * Provides the combinations of engines and execution modes to be tested.
 */
class EngineFrameworkProvider : ArgumentsProvider {

    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
        EngineProvider.engines()
            .flatMap { engine -> listOf(Arguments.of(engine, ExecutionMode.Reflection), Arguments.of(engine, ExecutionMode.Auto)) }
            .stream()

}
