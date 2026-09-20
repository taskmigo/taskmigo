/// Installation migration tasks executed after schema migration and before runtime applications start.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "authorization :: object",
        "authorization :: provisioning",
        "authorization :: role",
        "authorization :: spi",
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
