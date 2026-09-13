package io.taskmigo.authorization.spi;

import java.util.List;
import java.util.UUID;

/// Resolves the authoritative effective Statement snapshot required by one authorization operation.
public interface EffectiveStatementResolver {
    /// Returns every deduplicated effective Statement with its current persisted revision.
    ///
    /// Implementations must resolve from the authoritative persistence state for each authorization operation. The
    /// revision is a cheap freshness signal for derived artifacts and must identify the exact persisted Statement row
    /// state returned alongside it.
    ///
    /// @param userId the user whose effective authorization state is required
    /// @return every effective Statement exactly once with its persisted revision
    List<EffectiveStatement> resolve(UUID userId);
}
