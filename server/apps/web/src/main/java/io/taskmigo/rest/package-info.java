/// Defines the public REST API application boundary.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "authorization :: object",
        "authorization :: request",
        "authorization :: role",
        "authorization :: spi",
        "authorization :: statement",
        "identity :: user",
        "identity :: group",
    }
)
@NullMarked
package io.taskmigo.rest;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
