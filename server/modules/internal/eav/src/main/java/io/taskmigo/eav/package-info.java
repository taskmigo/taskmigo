/// Owns reusable versioned typed EAV schema, value, and query capabilities.
@ApplicationModule(allowedDependencies = { "query", "jpaquery" })
@NullMarked
package io.taskmigo.eav;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
