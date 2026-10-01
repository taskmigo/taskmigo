package io.taskmigo.audit.model;

import java.util.UUID;

/// Captures the authenticated actor at the time an audited mutation occurs.
///
/// @param id stable actor identifier
/// @param username actor username captured with the mutation
public record AuditActor(UUID id, String username) {}
