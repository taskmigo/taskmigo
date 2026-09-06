rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:policy",
    ":modules:database",
    ":modules:auth",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
