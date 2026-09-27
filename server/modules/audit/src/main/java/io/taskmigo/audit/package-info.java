/// Owns durable, append-only entity audit records and their query contract.
@ApplicationModule(allowedDependencies = { "foundation" })
@NullMarked
package io.taskmigo.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
