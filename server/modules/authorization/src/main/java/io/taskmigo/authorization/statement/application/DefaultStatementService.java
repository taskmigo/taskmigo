package io.taskmigo.authorization.statement.application;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates published runtime Statement commands and projection queries.
@Service
public class DefaultStatementService implements StatementService {

    private final StatementCommandService commands;
    private final StatementQueryRepository statements;

    public DefaultStatementService(StatementCommandService commands, StatementQueryRepository statements) {
        this.commands = commands;
        this.statements = statements;
    }

    @Override
    @Transactional
    public UUID create(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        try {
            return this.commands.createRuntime(code, description, effect, scope, method, path, policy);
        } catch (StatementRuleViolation exception) {
            throw badRequest(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        return this.statements.list(page, perPage);
    }

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

    @Override
    @Transactional(readOnly = true)
    public void requireStatements(Collection<UUID> ids) {
        if (!this.statements.containsAll(Set.copyOf(ids))) {
            throw new AuthorizationException("One or more Statements do not exist");
        }
    }

    private static AuthorizationException badRequest(StatementRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }
}
