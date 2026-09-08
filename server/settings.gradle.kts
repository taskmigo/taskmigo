rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:embedded-language",
    ":modules:database",
    ":modules:auth",
    ":modules:query-filtering",
    ":benchmarks:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
