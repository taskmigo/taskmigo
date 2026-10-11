/// Hosts the web executable application's driving adapters, framework-specific driven adapters, and composition.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "audit :: model",
        "audit :: query",
        "query",
        "authorization :: object",
        "authorization :: object-use-cases",
        "authorization :: object-target-resolution",
        "authorization :: request",
        "authorization :: request-use-cases",
        "authorization :: role",
        "authorization :: role-use-cases",
        "authorization :: statement",
        "authorization :: statement-use-cases",
        "identity :: user",
        "identity :: user-use-cases",
        "identity :: group",
        "identity :: group-use-cases",
        "identity :: configuration",
        "identity :: configuration-use-cases",
    }
)
@NullMarked
package io.taskmigo.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
