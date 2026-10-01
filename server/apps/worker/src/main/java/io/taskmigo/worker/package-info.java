/// Hosts the background-worker executable boundary. No background-job adapters are currently implemented; future
/// jobs belong under `adapter.in` and consume deliberate inbound ports.
@ApplicationModule
@NullMarked
package io.taskmigo.worker;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
