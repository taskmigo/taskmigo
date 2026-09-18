/// Owns opaque authorization subjects, contracts, and application use cases.
///
/// JPA adapters remain private in the owning persistence package.
@NamedInterface("subject")
@NullMarked
package io.taskmigo.authorization.subject;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
