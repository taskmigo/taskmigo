/// Owns authorization statements and their request- and object-enforcement flows.
@ApplicationModule(allowedDependencies = { "foundation", "language" })
@NullMarked
package io.taskmigo.authorization;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
