package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.statement.StatementException;
import io.taskmigo.authorization.statement.application.port.out.StatementCommandRepository;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementCode;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/// Adapts canonical Statement aggregate persistence to JPA without exposing revision metadata to the domain.
@Repository
public class JpaStatementCommandRepository implements StatementCommandRepository {

    private final StatementRepository statements;

    JpaStatementCommandRepository(StatementRepository statements) {
        this.statements = statements;
    }

    @Override
    public Optional<Statement> findByCode(StatementCode code) {
        return this.statements.findByCode(code.value()).map(StatementEntity::toDomain);
    }

    @Override
    public void save(Statement statement) {
        StatementEntity existing = this.statements.findById(statement.id()).orElse(null);
        if (existing == null) {
            try {
                this.statements.saveAndFlush(StatementEntity.from(statement));
            } catch (DataIntegrityViolationException exception) {
                throw new StatementException(
                    StatementException.Type.CONFLICT,
                    "Statement code already exists",
                    exception
                );
            }
            return;
        }
        existing.update(statement);
        this.statements.flush();
    }

    @Override
    public void delete(Statement statement) {
        this.statements.deleteById(statement.id());
        this.statements.flush();
    }
}
