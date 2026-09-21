package io.taskmigo.authorization.statement.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementCommandService;
import io.taskmigo.authorization.statement.application.port.out.StatementQueryRepository;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Coordinates published runtime Statement commands and projection queries.
public final class DefaultStatementService implements StatementService {

    private final StatementCommandService commands;
    private final StatementQueryRepository statements;
    private final TransactionRunner transactions;

    public DefaultStatementService(
        StatementCommandService commands,
        StatementQueryRepository statements,
        TransactionRunner transactions
    ) {
        this.commands = commands;
        this.statements = statements;
        this.transactions = transactions;
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
        return this.transactions.write(() -> {
            try {
                return this.commands.createRuntime(code, description, effect, scope, method, path, policy);
            } catch (StatementRuleViolation exception) {
                throw badRequest(exception);
            }
        });
    }

    @Override
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        return this.transactions.read(() -> this.statements.list(page, perPage));
    }

    @Override
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        return this.transactions.read(() -> this.statements.list(page, perPage, filter, authorization));
    }

    @Override
    public void requireStatements(Collection<UUID> ids) {
        this.transactions.read(() -> {
            if (!this.statements.containsAll(Set.copyOf(ids))) {
                throw new AuthorizationException("One or more Statements do not exist");
            }
            return Boolean.TRUE;
        });
    }

    private static AuthorizationException badRequest(StatementRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }
}
