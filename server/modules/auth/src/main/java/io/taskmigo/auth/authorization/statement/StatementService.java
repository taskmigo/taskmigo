package io.taskmigo.auth.authorization.statement;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.AuthorizationName;
import io.taskmigo.auth.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.resourcequery.ObjectAuthorizationPredicateBinder;
import io.taskmigo.auth.resourcequery.QueryPredicateBinder;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageException;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages canonical authorization Statements and validates their compiled policy modules.
@Service
public class StatementService {

    private final StatementRepository statements;
    private final ObjectAuthorizationService objectAuthorization;
    private final EmbeddedLanguageCompiler embeddedLanguageCompiler;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    StatementService(
        StatementRepository statements,
        ObjectAuthorizationService objectAuthorization,
        EmbeddedLanguageCompiler embeddedLanguageCompiler,
        QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder
    ) {
        this.statements = statements;
        this.objectAuthorization = objectAuthorization;
        this.embeddedLanguageCompiler = embeddedLanguageCompiler;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    /// Validates and persists a Statement with a server-assigned stable identifier.
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
        String validName = AuthorizationName.required(name, "name");
        if (this.statements.existsByName(validName)) {
            throw new AuthorizationException("Statement name already exists");
        }
        Effect validEffect = required(effect, "effect");
        Scope validScope = required(scope, "scope");
        String validMethod = required(method, "target.api.method");
        if (
            !"*".equals(validMethod) &&
            !Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE").contains(validMethod)
        ) {
            throw new AuthorizationException("target.api.method must be a valid HTTP method");
        }
        String validPath = required(path, "target.api.path");
        if (validPath.length() > 2000) {
            throw new AuthorizationException("target.api.path must not exceed 2000 characters");
        }
        try {
            Pattern.compile(validPath);
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("target.api.path must be a valid regular expression");
        }
        if (validMethod.length() > 16) {
            throw new AuthorizationException("target.api.method must not exceed 16 characters");
        }
        String validPolicy = requiredPolicy(policy);
        try {
            if (validScope == Scope.REQUEST) {
                this.embeddedLanguageCompiler.compile(validPolicy, AuthorizationEmbeddedLanguageSchemas.request());
            } else {
                this.objectAuthorization.validatePolicy(validPolicy, validMethod, validPath);
            }
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
        UUID id = UUID.randomUUID();
        this.statements.save(
            new StatementEntity(
                id,
                validName,
                description,
                validEffect,
                validScope,
                validMethod,
                validPath,
                validPolicy
            )
        );
        return id;
    }

    /// Reconciles a managed Statement by stable name without changing its identifier.
    @Transactional
    public UUID reconcile(
        @Nullable String name,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        String validName = AuthorizationName.required(name, "name");
        StatementEntity existing = this.statements.findByName(validName).orElse(null);
        if (existing == null) {
            return this.create(validName, description, effect, scope, method, path, policy);
        }
        Effect validEffect = required(effect, "effect");
        Scope validScope = required(scope, "scope");
        String validMethod = required(method, "target.api.method");
        if (
            !"*".equals(validMethod) &&
            !Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE").contains(validMethod)
        ) {
            throw new AuthorizationException("target.api.method must be a valid HTTP method");
        }
        String validPath = required(path, "target.api.path");
        try {
            Pattern.compile(validPath);
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("target.api.path must be a valid regular expression");
        }
        String validPolicy = requiredPolicy(policy);
        try {
            if (validScope == Scope.REQUEST) {
                this.embeddedLanguageCompiler.compile(validPolicy, AuthorizationEmbeddedLanguageSchemas.request());
            } else {
                this.objectAuthorization.validatePolicy(validPolicy, validMethod, validPath);
            }
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
        existing.description = description;
        existing.effect = validEffect;
        existing.scope = validScope;
        existing.method = validMethod;
        existing.path = validPath;
        existing.policy = validPolicy;
        this.statements.flush();
        return existing.id;
    }

    /// Lists Statements in stable identifier order for offset pagination.
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        var result = this.statements.findAllBy(PageRequest.of(page - 1, perPage, Sort.by("id")));
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /// Lists Statements by binding both opaque predicates before pagination.
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.statements.findAll(this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)), pageable);
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /// Validates that every supplied Statement id exists.
    ///
    /// @param ids the Statement ids to validate
    /// @throws AuthorizationException when any supplied Statement does not exist
    @Transactional(readOnly = true)
    public void requireStatements(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        if (this.statements.findAllById(requestedIds).size() != requestedIds.size()) {
            throw new AuthorizationException("One or more Statements do not exist");
        }
    }

    /// Resolves a Statement name for bootstrap references, including persisted definitions from prior runs.
    @Transactional(readOnly = true)
    public UUID requireByName(String name) {
        return this.statements
            .findByName(AuthorizationName.required(name, "statement reference"))
            .map(entity -> entity.id)
            .orElseThrow(() ->
                new IllegalStateException("Built-in authorization Statement reference does not exist: " + name)
            );
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AuthorizationException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new AuthorizationException(field + " is required");
        }
        return value;
    }

    private static String requiredPolicy(@Nullable String policy) {
        if (policy == null || policy.isBlank()) {
            throw new AuthorizationException("policy must not be blank");
        }
        return policy;
    }
}
