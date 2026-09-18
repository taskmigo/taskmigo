package io.taskmigo.authorization.statement.internal;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Orchestrates Statement use cases without depending on a persistence implementation.
@Service
public class DefaultStatementService implements StatementService {

    private final StatementStore statements;
    private final StatementPolicyValidator policyValidator;

    public DefaultStatementService(StatementStore statements, StatementPolicyValidator policyValidator) {
        this.statements = statements;
        this.policyValidator = policyValidator;
    }

    /// Validates and creates a canonical Statement with a server-assigned identifier.
    @Override
    @Transactional
    public UUID create(
        @Nullable String name,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        StatementDefinition definition = this.validate(name, description, effect, scope, method, path, policy);
        if (this.statements.existsByName(definition.name())) {
            throw new AuthorizationException("Statement name already exists");
        }
        return this.statements.create(definition);
    }

    /// Lists Statements in stable identifier order for offset pagination.
    @Override
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        return this.statements.list(page, perPage);
    }

    /// Lists Statements after applying the supplied query and authorization predicates.
    @Override
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        return this.statements.list(page, perPage, filter, authorization);
    }

    /// Validates that every supplied Statement id exists.
    @Override
    @Transactional(readOnly = true)
    public void requireStatements(Collection<UUID> ids) {
        if (!this.statements.containsAll(ids)) {
            throw new AuthorizationException("One or more Statements do not exist");
        }
    }

    private StatementDefinition validate(
        @Nullable String name,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        return this.policyValidator.validate(name, description, effect, scope, method, path, policy);
    }
}
