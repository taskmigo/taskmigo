/// Defines the public REST API application boundary.
@ApplicationModule(allowedDependencies = { "auth :: *", "foundation", "query" })
@NullMarked
package io.taskmigo.rest;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
