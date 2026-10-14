/// Owns immutable entity-audit records, synchronous append semantics, and audit queries.
@ApplicationModule(allowedDependencies = { "foundation" })
@NullMarked
package io.taskmigo.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
