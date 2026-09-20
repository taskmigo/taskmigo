/// Publishes User-facing application contracts.
///
/// Domain behavior, application implementation, and persistence adapters live in inward-facing subpackages and are not
/// part of the named interface.
@NamedInterface("user")
@NullMarked
package io.taskmigo.identity.user;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
