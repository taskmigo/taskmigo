/// Owns Identity resources and their trusted persistence integrations.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "database :: criteria",
        "query",
        "query :: model",
        "authorization :: object",
        "authorization :: object-model",
        "authorization :: subject-resolution-port",
        "authorization :: subject",
        "authorization :: subject-input",
    }
)
@NullMarked
package io.taskmigo.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
