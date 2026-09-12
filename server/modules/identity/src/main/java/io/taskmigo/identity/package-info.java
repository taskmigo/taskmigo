/// Owns identity resources and their trusted persistence integrations.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "query :: persistence",
        "authorization :: core",
        "authorization :: object",
        "authorization :: object-persistence",
        "authorization :: request",
        "authorization :: statement",
        "authorization :: role",
    }
)
@NullMarked
package io.taskmigo.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
