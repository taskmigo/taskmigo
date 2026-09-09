rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:language",
    ":modules:query",
    ":modules:authorization",
    ":modules:database",
    ":modules:auth",
    ":modules:identity",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
