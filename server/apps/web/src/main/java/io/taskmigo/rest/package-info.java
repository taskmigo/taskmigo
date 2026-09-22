/// Defines the public REST API application boundary.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "authorization :: object",
        "authorization :: object-input",
        "authorization :: object-target-resolution-port",
        "authorization :: request",
        "authorization :: role",
        "authorization :: role-input",
        "authorization :: statement",
        "authorization :: statement-input",
        "identity :: user",
        "identity :: user-input",
        "identity :: group",
        "identity :: group-input",
    }
)
@NullMarked
package io.taskmigo.rest;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
