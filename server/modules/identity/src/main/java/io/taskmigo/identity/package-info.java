/// Owns Identity resources and their trusted persistence integrations.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "query :: persistence",
        "authorization :: object",
        "authorization :: object-persistence",
        "authorization :: spi",
        "authorization :: role",
        "authorization :: subject",
    }
)
@NullMarked
package io.taskmigo.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
