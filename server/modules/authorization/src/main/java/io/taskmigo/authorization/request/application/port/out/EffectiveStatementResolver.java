package io.taskmigo.authorization.request.application.port.out;

import java.util.List;
import java.util.UUID;

/// Resolves the authoritative effective Statement snapshot required by one authorization operation.
public interface EffectiveStatementResolver {
    /// Returns every deduplicated effective Statement with its current persisted revision.
    ///
    /// Implementations must resolve from authoritative persistence state for each authorization operation. The
    /// revision identifies the exact persisted Statement row state returned alongside it.
    ///
    /// @param userId the user whose effective authorization state is required
    /// @return every effective Statement exactly once with its persisted revision
    List<EffectiveStatement> resolve(UUID userId);
}
