/// Publishes User data/error contracts shared with runtime applications.
///
/// User use cases are exposed separately through the `user-use-cases` named interface; domain, application
/// implementations, persistence, integrations, and configuration remain internal to Identity.
@NamedInterface("user")
@NullMarked
package io.taskmigo.identity.user;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
