/// Owns authorization policy lifecycle, request decisions, and persistence-neutral object predicates.
@ApplicationModule(allowedDependencies = { "foundation", "language", "language :: ast" })
@NullMarked
package io.taskmigo.authorization;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
