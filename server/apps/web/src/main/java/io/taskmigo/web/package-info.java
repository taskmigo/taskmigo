/// Hosts the web executable application's driving adapters, framework-specific driven adapters, and composition.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "authorization :: object",
        "authorization :: object-input",
        "authorization :: object-target-resolution-port",
        "authorization :: request",
        "authorization :: request-input",
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
package io.taskmigo.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
