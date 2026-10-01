/// Owns immutable entity-audit records, synchronous append semantics, and audit queries.
@ApplicationModule(allowedDependencies = { "foundation", "database" })
@NullMarked
package io.taskmigo.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
