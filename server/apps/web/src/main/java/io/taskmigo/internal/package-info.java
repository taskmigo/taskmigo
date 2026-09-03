/// Contains web-only infrastructure that is not part of the public REST API contract.
@ApplicationModule(allowedDependencies = { "rest :: *", "auth :: *", "foundation" })
@NullMarked
package io.taskmigo.internal;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
