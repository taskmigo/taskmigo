/// Hosts the migration installation application ports/services, startup driving adapters, framework-backed driven adapters, and composition.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
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
