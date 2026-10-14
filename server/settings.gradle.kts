pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "taskmigo"

include(
    ":modules:foundation:core",
    ":modules:foundation:spring",
    ":modules:language",
    ":modules:query",
    ":modules:jpa-query",
    ":modules:authorization",
    ":modules:database",
    ":modules:audit",
    ":modules:identity",
    ":benchmarks:authorization",
    ":testing:architecture",
    ":apps:migration",
    ":apps:web",
    ":apps:worker",
)
