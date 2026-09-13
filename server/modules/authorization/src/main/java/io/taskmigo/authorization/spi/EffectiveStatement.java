package io.taskmigo.authorization.spi;

import io.taskmigo.authorization.statement.StatementInfo;
import java.time.Instant;
import java.util.Objects;

/// One effective persisted Statement together with the revision that produced its current execution state.
///
/// `updatedAt` is persistence metadata, not part of the canonical Statement contract. Persistence adapters must advance
/// it whenever the corresponding Statement row changes so derived execution artifacts can be reused safely.
public record EffectiveStatement(StatementInfo statement, Instant updatedAt) {
    public EffectiveStatement {
        Objects.requireNonNull(statement);
        Objects.requireNonNull(updatedAt);
    }
}
