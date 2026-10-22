package org.genhttp.playground.samples

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.files.Assets
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.layouting.Layout

object LayoutingSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Layouts allow you to structure your web application and divide it into
         * different logical parts. The layout will route incoming requests to the designated
         * handler. Layouts can be nested as needed.
         *
         * See https://genhttp-kt.genhttp.run/documentation/content/handlers/layouting/
         *
         */

        // serve static files from the current directory
        val assets = Assets.from("./")

        // define a simple REST API
        val api = Inline.create()
            .get { "Hello World!" }

        // assemble our application from the parts above and
        // register them at the given URLs
        return Layout.create()
            .add("assets", assets) // e.g. GET http://localhost:8080/assets/build.gradle.kts
            .add("api", api) // e.g. GET http://localhost:8080/api/
    }

}
