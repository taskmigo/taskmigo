/// Provides version-neutral API authentication and authorization policy with version-specific security error rendering.
///
/// New API versions can implement [ApiSecurityErrorRenderer] without coupling the shared security layer to a response
/// schema.
@NullMarked
package io.taskmigo.web.security;

import org.jspecify.annotations.NullMarked;
