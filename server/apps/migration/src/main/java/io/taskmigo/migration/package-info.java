/// Runs Taskmigo's one-shot installation and database migration workflow.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "foundation :: jackson",
        "authorization :: object",
        "authorization :: object-target-resolution-port",
        "authorization :: provisioning",
        "authorization :: provisioning-input",
        "authorization :: role",
        "authorization :: statement",
        "identity :: provisioning",
        "identity :: provisioning-input",
        "identity :: user",
        "identity :: group",
    }
)
@NullMarked
package io.taskmigo.migration;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
