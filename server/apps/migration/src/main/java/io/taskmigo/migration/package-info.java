/// Composes installation-time migration and reconciliation through published module contracts.
@ApplicationModule(
    allowedDependencies = {
        "authorization :: object",
        "authorization :: provisioning",
        "authorization :: role",
        "authorization :: spi",
        "authorization :: statement",
        "identity :: group",
        "identity :: provisioning",
        "identity :: user",
        "security :: oauth",
    }
)
@NullMarked
package io.taskmigo.migration;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
