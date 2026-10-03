package io.taskmigo.identity.user.application.port.out;

import java.util.UUID;

/// Scrubs User PII from audit history during the atomic tombstone transaction.
public interface UserAuditScrubber {
    void scrub(UUID userId);
}
