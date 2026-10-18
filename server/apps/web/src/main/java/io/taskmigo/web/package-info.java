/// Hosts the web executable application's API, Security/session, and OAuth runtime integration.
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
        "eav :: schema-model",
        "eav :: schema-use-cases",
        "ticket :: ticket-use-cases",
        "ticket :: ticket-type-use-cases",
        "ticket :: ticket-type-model",
    }
)
@NullMarked
package io.taskmigo.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
