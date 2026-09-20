/// Publishes User data/error contracts shared with driving adapters.
///
/// User use cases are exposed separately through the `user-input` named interface; domain, application implementation,
/// outbound ports, and driven adapters remain internal to Identity.
@NamedInterface("user")
@NullMarked
package io.taskmigo.identity.user;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
