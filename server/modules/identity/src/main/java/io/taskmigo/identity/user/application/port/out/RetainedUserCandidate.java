package io.taskmigo.identity.user.application.port.out;

import java.time.Instant;
import java.util.UUID;

/// Identifies one retained User candidate in stable retention-time/id keyset order.
public record RetainedUserCandidate(UUID id, Instant retainedAt) {}
