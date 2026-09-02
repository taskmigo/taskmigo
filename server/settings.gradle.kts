rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:auth",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
