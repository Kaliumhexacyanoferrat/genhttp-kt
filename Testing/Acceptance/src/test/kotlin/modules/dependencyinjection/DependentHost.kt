package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.dependencyinjection.addDependencyInjection
import org.genhttp.testing.TestHost

import org.koin.dsl.koinApplication
import org.koin.dsl.module

object DependentHost {

    suspend fun run(app: HandlerBuilder, engine: ServerEngine = ServerEngine.Internal): TestHost {
        val host = TestHost(app.build(), engine = engine)

        val koin = koinApplication {
            modules(module {
                single { AwesomeService() }
                single { AnotherAwesomeService() }
            })
        }.koin

        host.host.addDependencyInjection(koin)

        host.start()

        return host
    }

}

class AwesomeService {

    fun doWork(): String = "42"

}

class AnotherAwesomeService {

    fun doWork(): String = "24"

}
