package io.taskmigo.authorization.statement.application;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.domain.Statement;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Access-Control-internal Statement commands shared by runtime and managed provisioning paths.
public interface StatementCommandService {
    UUID createRuntime(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );

    StatementMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );

    Optional<Statement> findByCode(@Nullable String code);

    void delete(Statement statement);
}
