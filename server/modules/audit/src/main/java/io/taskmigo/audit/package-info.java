/// Owns immutable audit events, durable audit-log persistence, and audit history queries.
@ApplicationModule(allowedDependencies = { "foundation" })
@NullMarked
package io.taskmigo.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
