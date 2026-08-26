/// Owns projects, project membership, and effective project authorization.
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "organization", "user", "group", "access", "foundation" }
)
@org.jspecify.annotations.NullMarked
package io.taskmigo.project;
