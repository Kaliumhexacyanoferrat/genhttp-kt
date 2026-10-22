package org.genhttp.playground.samples

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.fromDirectory
import org.genhttp.modules.singlepageapplications.SinglePageApplication
import org.genhttp.modules.staticwebsites.StaticWebsite

object StaticFileSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Serves files from a given directory or resource tree.
         *
         * See https://genhttp.org/documentation/content/handlers/static-content/
         *
         */

        // e.g. GET http://localhost:8080/build.gradle.kts
        return Assets.from("./")
    }

}

object StaticWebsiteSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Serves a static website from a directory (or resource tree in general).
         *
         * See https://genhttp.org/documentation/content/frameworks/static-websites/
         *
         */
        val files = ResourceTree.fromDirectory("/var/www")

        return StaticWebsite.from(files)
    }

}

object SinglePageApplicationSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Serves a single page application (SPA) from a directory (or resource tree in general).
         *
         * See https://genhttp.org/documentation/content/frameworks/single-page-applications/
         *
         */
        val files = ResourceTree.fromDirectory("/var/app")

        return SinglePageApplication.from(files)
    }

}
