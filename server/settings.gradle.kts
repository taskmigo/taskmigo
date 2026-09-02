rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:api",
    ":modules:auth",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
