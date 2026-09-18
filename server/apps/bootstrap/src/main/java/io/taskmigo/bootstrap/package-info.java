/// Installation bootstrap tasks executed after schema migration and before runtime applications start.
@ApplicationModule(
    allowedDependencies = {
        "authorization :: object",
        "authorization :: provisioning",
        "authorization :: role",
        "authorization :: spi",
        "authorization :: statement",
        "identity :: provisioning",
        "identity :: user",
        "identity :: group",
    }
)
@NullMarked
package io.taskmigo.bootstrap;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
