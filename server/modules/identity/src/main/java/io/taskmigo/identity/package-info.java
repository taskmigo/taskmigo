/// Owns Identity resources and their trusted persistence integrations.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "audit :: append",
        "audit :: events",
        "audit :: model",
        "audit :: privacy",
        "jpaquery",
        "query",
        "query :: model",
        "authorization :: object",
        "authorization :: object-model",
        "authorization :: subject-resolution",
        "authorization :: subject",
        "authorization :: subject-use-cases",
    }
)
@NullMarked
package io.taskmigo.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
