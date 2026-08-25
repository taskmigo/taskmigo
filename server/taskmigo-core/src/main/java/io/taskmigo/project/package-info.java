/// Owns projects, project membership, and effective project authorization.
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"organization", "user", "group", "access"}
)
@org.jspecify.annotations.NullMarked
package io.taskmigo.project;
