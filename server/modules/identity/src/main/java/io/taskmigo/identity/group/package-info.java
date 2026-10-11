/// Publishes Group data/error contracts shared with Taskmigo runtime applications.
///
/// Group use cases are exposed separately through the `group-use-cases` named interface; domain, application
/// implementations, persistence, and configuration remain internal to Identity.
@NamedInterface("group")
@NullMarked
package io.taskmigo.identity.group;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
