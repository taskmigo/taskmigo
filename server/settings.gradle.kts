rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:auth",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
