plugins {
    id("genhttp.package")
}

description = "Contains all modules of the genhttp-kt framework suite, including the Netty engine"

dependencies {
    api(project(":api"))
    api(project(":engine-netty"))

    api(project(":modules-api-browsing"))
    api(project(":modules-archives"))
    api(project(":modules-authentication"))
    api(project(":modules-client-caching"))
    api(project(":modules-compression"))
    api(project(":modules-controllers"))
    api(project(":modules-conversion"))
    api(project(":modules-dependency-injection"))
    api(project(":modules-directory-browsing"))
    api(project(":modules-error-handling"))
    api(project(":modules-files"))
    api(project(":modules-functional"))
    api(project(":modules-i18n"))
    api(project(":modules-inspection"))
    api(project(":modules-io"))
    api(project(":modules-layouting"))
    api(project(":modules-load-balancing"))
    api(project(":modules-open-api"))
    api(project(":modules-pages"))
    api(project(":modules-practices"))
    api(project(":modules-protobuf"))
    api(project(":modules-redirects"))
    api(project(":modules-reflection"))
    api(project(":modules-reverse-proxy"))
    api(project(":modules-security"))
    api(project(":modules-server-sent-events"))
    api(project(":modules-single-page-applications"))
    api(project(":modules-static-websites"))
    api(project(":modules-virtual-hosting"))
    api(project(":modules-webservices"))
    api(project(":modules-websockets"))
}
