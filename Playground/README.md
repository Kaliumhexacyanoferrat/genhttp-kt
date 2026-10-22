# genhttp-kt Playground

The playground provides code samples for quick reference and allows developers to
quickly validate things.

| Sample                                                               | Description                                                                      |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [Layouting](./src/main/kotlin/samples/LayoutingSample.kt)                            | Allows an app to use multiple handlers by adding routing.                        |
| [Webservices](./src/main/kotlin/samples/WebserviceSample.kt)                         | Implements a REST webservice via a handler class.                                |
| [Functional](./src/main/kotlin/samples/FunctionalSample.kt)                          | Uses lambdas to implement a web service (similar to Ktor routes or ASP.NET Core Minimal API). |
| [Controllers](./src/main/kotlin/samples/ControllerSample.kt)                         | Uses controllers to implement a web service (similar to Spring MVC).       |
| [Static Files](./src/main/kotlin/samples/StaticSamples.kt)                        | Serves static files from a directory.                                            |
| [Static Websites](./src/main/kotlin/samples/StaticSamples.kt)                  | Hosts a static website (with `index.html` support).                              |
| [Single Page Applications](./src/main/kotlin/samples/StaticSamples.kt) | Hosts a SPA such as a React or Angular application.                              |

Run the playground with `./gradlew :playground:run` (the active sample is selected in [Program.kt](./src/main/kotlin/Program.kt)).
