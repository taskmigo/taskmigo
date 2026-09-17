pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:language",
    ":modules:query",
    ":modules:access-control",
    ":modules:database",
    ":modules:identity",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)

project(":modules:access-control").projectDir = file("modules/authorization")
