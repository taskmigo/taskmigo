rootProject.name = "taskmigo"

include(
    ":modules:foundation",
    ":modules:database",
    ":modules:organization",
    ":modules:project",
    ":apps:web",
    ":apps:worker",
)
