/// Publishes Group data/error contracts shared with driving adapters.
///
/// Group use cases are exposed separately through the `group-input` named interface; domain, application implementation,
/// outbound ports, and driven adapters remain internal to Identity.
@NamedInterface("group")
@NullMarked
package io.taskmigo.identity.group;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
