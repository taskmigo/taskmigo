package io.taskmigo.authorization.statement.application;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementException;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.api.StatementService;
import io.taskmigo.authorization.statement.application.internal.StatementCommandService;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.ListQuerySpec;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates published runtime Statement commands and projection queries.
@Transactional
public class DefaultStatementService implements StatementService {

    private final StatementCommandService commands;
    private final StatementQueryRepository statements;

    public DefaultStatementService(StatementCommandService commands, StatementQueryRepository statements) {
        this.commands = commands;
        this.statements = statements;
    }

    @Override
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
    public void deleteStatement(UUID id) {
        var statement = this.commands
            .findById(id)
            .orElseThrow(() -> new StatementException(StatementException.Type.NOT_FOUND, "Statement not found"));
        this.commands.delete(statement);
    }

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        return this.statements.list(page, perPage);
    }

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        ListQuerySpec<StatementInfo> query,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        return this.statements.list(page, perPage, query, authorization);
    }

    @Transactional(readOnly = true)
    @Override
    public void requireStatements(Collection<UUID> ids) {
        if (!this.statements.containsAll(Set.copyOf(ids))) {
            throw new AuthorizationException("One or more Statements do not exist");
        }
    }

    private static AuthorizationException badRequest(StatementRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }
}
