/// Owns reusable authorization Statements, Roles, and principal Role assignments.
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "acl", "foundation", "organization", "user" }
)
@org.jspecify.annotations.NullMarked
package io.taskmigo.access;
