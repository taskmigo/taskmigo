/// Hosts the background-worker executable boundary and its runtime-owned background jobs.
///
/// Reusable business behavior remains in its owning module; Worker packages schedule and compose those published use
/// cases. Worker currently consumes only the Identity User use-case surface.
@ApplicationModule(allowedDependencies = "identity :: user-use-cases")
@NullMarked
package io.taskmigo.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
