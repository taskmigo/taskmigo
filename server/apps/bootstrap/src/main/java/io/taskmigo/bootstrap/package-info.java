/// Installation bootstrap tasks executed after schema migration and before runtime applications start.
@ApplicationModule(
    allowedDependencies = {
        "authorization :: object",
        "authorization :: role",
        "authorization :: spi",
        "authorization :: statement",
        "identity :: user",
        "identity :: group",
        "identity :: oauth",
        "identity :: role-management",
        "identity :: statement-management",
    }
)
@NullMarked
package io.taskmigo.bootstrap;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
