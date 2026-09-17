/// Owns the Access Control bounded context: authorization policy lifecycle, Roles, Statements, subject grants, and decisions.
@ApplicationModule(
    allowedDependencies = {
        "foundation",
        "language",
        "language :: ast",
        "query",
        "query :: persistence",
        "database :: criteria",
    }
)
@NullMarked
package io.taskmigo.authorization;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
