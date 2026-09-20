pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "taskmigo"

include(
    ":tooling:checkstyle",
    ":modules:foundation",
    ":modules:language",
    ":modules:query",
    ":modules:access-control",
    ":modules:database",
    ":modules:identity",
    ":benchmarks:authorization",
    ":testing:architecture",
    ":apps:migration",
    ":apps:web",
    ":apps:worker",
)

project(":modules:access-control").projectDir = file("modules/authorization")
