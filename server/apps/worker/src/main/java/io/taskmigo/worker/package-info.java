/// Runs background jobs.
@ApplicationModule(allowedDependencies = { "identity" })
@NullMarked
package io.taskmigo.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
