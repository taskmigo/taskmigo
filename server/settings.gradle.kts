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
    ":modules:internal:language",
    ":modules:query",
    ":modules:internal:jpa-query",
    ":modules:authorization",
    ":modules:database",
    ":modules:audit",
    ":modules:identity",
    ":modules:internal:eav",
    ":modules:ticket",
    ":benchmarks:authorization",
    ":testing:architecture",
    ":apps:migration",
    ":apps:openapi-generator",
    ":apps:web",
    ":apps:worker",
)
