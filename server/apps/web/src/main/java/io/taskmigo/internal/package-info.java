/// Contains authorization-server and OAuth web infrastructure pending composition-root normalization.
@ApplicationModule(
    allowedDependencies = {
        "authorization :: request",
        "authorization :: request-input",
        "identity :: user",
        "identity :: user-input",
    }
)
@NullMarked
package io.taskmigo.internal;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
