package io.taskmigo.authorization.statement.application.port.out;

import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementCode;
import java.util.Optional;

/// Persists canonical Statement aggregate state for command use cases.
public interface StatementCommandRepository {
    Optional<Statement> findByCode(StatementCode code);

    void save(Statement statement);

    void delete(Statement statement);
}
