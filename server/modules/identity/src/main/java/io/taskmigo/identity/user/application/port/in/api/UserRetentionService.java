package io.taskmigo.identity.user.application.port.in.api;

import java.time.Instant;

/// Defines background User-retention lifecycle maintenance.
public interface UserRetentionService {
    /// Tombstones retained Users whose current retention deadline has expired.
    int purgeExpiredUsers(Instant now);
}
