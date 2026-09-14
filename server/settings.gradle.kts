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
    ":modules:authorization",
    ":modules:database",
    ":modules:security",
    ":modules:identity",
    ":benchmarks:authorization",
    ":apps:migration",
    ":apps:web",
    ":apps:worker",
)
