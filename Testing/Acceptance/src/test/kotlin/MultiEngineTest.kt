package org.genhttp.testing.acceptance

import org.genhttp.api.infrastructure.ServerEngine

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations

import java.util.stream.Stream

/**
 * When placed on a test function, injects the engines that are target to be tested into the test function
 * (as a parameter of type [ServerEngine]).
 *
 * The engines can be restricted using the `TEST_ENGINE` environment variable. The ioxide engine is tested on Linux
 * only, the only platform its io_uring runtime exists for.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest(name = "{displayName} ({0})")
@ArgumentsSource(EngineProvider::class)
annotation class MultiEngineTest

/**
 * Provides the engines to be tested.
 */
class EngineProvider : ArgumentsProvider {

    override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> =
        engines().stream().map { Arguments.of(it) }

    companion object {

        private val IoxideSupported = System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

        fun engines(): List<ServerEngine> {
            val engine = System.getenv("TEST_ENGINE")

            if (engine == null) {
                val engines = mutableListOf(ServerEngine.Internal, ServerEngine.Netty)

                if (IoxideSupported) {
                    engines.add(ServerEngine.Ioxide)
                }

                return engines
            }

            val found = ServerEngine.entries.firstOrNull { it.name.equals(engine, ignoreCase = true) }
                ?: throw IllegalStateException("Engine '$engine' is not supported")

            if (found == ServerEngine.Ioxide && !IoxideSupported) {
                throw IllegalStateException("The ioxide engine is Linux-only and cannot be tested on this platform")
            }

            return listOf(found)
        }

    }

}
