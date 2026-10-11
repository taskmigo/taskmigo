/// Owns shared JPA-backed query-schema infrastructure used by persistence adapters.
@ApplicationModule(allowedDependencies = { "foundation", "query", "query :: model", "database :: criteria" })
@NullMarked
package io.taskmigo.jpaquery;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
