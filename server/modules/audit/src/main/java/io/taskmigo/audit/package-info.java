/// Owns durable entity-audit records, query semantics, and asynchronous append processing.
@ApplicationModule(allowedDependencies = { "foundation", "database" })
@NullMarked
package io.taskmigo.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
