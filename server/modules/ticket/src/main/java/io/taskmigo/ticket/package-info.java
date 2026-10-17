/// Owns Ticket Type and Ticket business semantics while consuming generic EAV capabilities.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "query",
        "query :: model",
        "jpaquery",
        "authorization :: object",
        "authorization :: object-model",
        "authorization :: jpa-query-binding",
        "eav :: query",
        "eav :: query-jpa",
        "eav :: schema-model",
        "eav :: schema-use-cases",
        "eav :: value-use-cases",
    }
)
@NullMarked
package io.taskmigo.ticket;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
