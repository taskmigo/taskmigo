/// Runs Taskmigo's one-shot installation and database migration workflow.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "authorization :: object",
        "authorization :: object-target-resolution",
        "authorization :: provisioning",
        "authorization :: provisioning-use-cases",
        "authorization :: role",
        "authorization :: statement",
        "identity :: provisioning",
        "identity :: provisioning-use-cases",
        "identity :: user",
        "identity :: group",
    }
)
@NullMarked
package io.taskmigo.migration;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
