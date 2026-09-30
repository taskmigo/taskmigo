/// Hosts background-job driving adapters and the durable audit worker composition.
@ApplicationModule(allowedDependencies = { "audit :: append-input", "audit :: jobrunr" })
@NullMarked
package io.taskmigo.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
