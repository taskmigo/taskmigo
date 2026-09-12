/// Owns identity resources and their trusted persistence integrations.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "language",
        "query",
        "authorization :: core",
        "authorization :: object",
        "authorization :: request",
        "authorization :: statement",
        "authorization :: role",
    }
)
@NullMarked
package io.taskmigo.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
