package io.taskmigo.authorization.statement.application;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementCode;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Applies canonical Statement mutations through a domain-shaped command repository.
@Service
public class DefaultStatementCommandService implements StatementCommandService {

    private final StatementCommandRepository statements;

    public DefaultStatementCommandService(StatementCommandRepository statements) {
        this.statements = statements;
    }

    @Override
    @Transactional
    public UUID createRuntime(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        Statement statement = Statement.create(
            UUID.randomUUID(),
            code,
            description,
            effect,
            scope,
            method,
            path,
            policy
        );
        if (this.statements.findByCode(statement.code()).isPresent()) {
            throw StatementRuleViolation.duplicateCode();
        }
        this.statements.save(statement);
        return statement.id();
    }

    @Override
    @Transactional
    public StatementMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        StatementCode normalizedCode = StatementCode.of(code);
        Statement existing = this.statements.findByCode(normalizedCode).orElse(null);
        if (existing == null) {
            Statement created = Statement.create(
                UUID.randomUUID(),
                normalizedCode.value(),
                description,
                effect,
                scope,
                method,
                path,
                policy
            );
            this.statements.save(created);
            return new StatementMutationResult(created.id(), true, true);
        }

        boolean changed = existing.reconcile(description, effect, scope, method, path, policy);
        if (changed) {
            this.statements.save(existing);
        }
        return new StatementMutationResult(existing.id(), false, changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Statement> findByCode(@Nullable String code) {
        return this.statements.findByCode(StatementCode.of(code));
    }

    @Override
    @Transactional
    public void delete(Statement statement) {
        this.statements.delete(statement);
    }
}
