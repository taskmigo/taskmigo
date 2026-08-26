rootProject.name = "taskmigo"

include(
    ":modules:database",
    ":modules:organization",
    ":modules:project",
    ":apps:web",
    ":apps:worker",
)
