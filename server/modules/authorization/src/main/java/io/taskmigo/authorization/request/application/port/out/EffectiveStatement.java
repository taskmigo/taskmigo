package io.taskmigo.authorization.request.application.port.out;

import io.taskmigo.authorization.statement.StatementInfo;
import java.util.Objects;

/// One authoritative effective Statement resolved for the current authorization operation.
public record EffectiveStatement(StatementInfo statement) {
    public EffectiveStatement {
        Objects.requireNonNull(statement);
    }
}
