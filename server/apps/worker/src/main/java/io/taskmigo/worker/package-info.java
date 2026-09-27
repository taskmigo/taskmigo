/// Hosts background-job driving adapters and the JobRunr execution boundary.
@ApplicationModule(allowedDependencies = { "audit :: input", "audit :: model" })
@NullMarked
package io.taskmigo.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
