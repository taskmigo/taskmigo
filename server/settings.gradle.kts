rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:identity",
    ":modules:access",
    ":modules:authorization",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
