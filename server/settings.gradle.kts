rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:embedded-language",
    ":modules:database",
    ":modules:auth",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
