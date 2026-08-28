rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:identity",
    ":modules:organization",
    ":modules:project",
    ":apps:bootstrap",
    ":apps:web",
    ":apps:worker",
)
