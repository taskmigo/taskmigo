rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:api-foundation",
    ":modules:auth",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
