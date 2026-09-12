/// Owns generic query contracts, filter compilation, and persistence-neutral query predicates.
@ApplicationModule(allowedDependencies = { "foundation", "language", "language :: ast" })
@NullMarked
package io.taskmigo.query;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
