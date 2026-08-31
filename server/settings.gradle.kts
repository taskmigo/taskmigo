rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:identity",
    ":modules:organization",
    ":modules:authorization",
    ":modules:project",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
