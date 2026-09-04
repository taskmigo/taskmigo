package io.taskmigo.auth.authorization.statement;

import io.taskmigo.auth.authorization.condition.AuthorizationException;
import io.taskmigo.auth.authorization.condition.AuthorizationName;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.foundation.OffsetPage;
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

/// Manages canonical authorization Statements without applying them to requests yet.
@Service
public class StatementService {

    private final StatementRepository statements;
    private final ObjectAuthorizationService objectAuthorization;

    StatementService(StatementRepository statements, ObjectAuthorizationService objectAuthorization) {
        this.statements = statements;
        this.objectAuthorization = objectAuthorization;
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
        if (this.statements.existsByName(validName)) throw new AuthorizationException("Statement name already exists");
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
        if (validPath.length() > 2000) throw new AuthorizationException(
            "target.api.path must not exceed 2000 characters"
        );
        try {
            Pattern.compile(validPath);
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("target.api.path must be a valid regular expression");
        }
        if (validMethod.length() > 16) throw new AuthorizationException(
            "target.api.method must not exceed 16 characters"
        );
        validatePolicy(policy);
        UUID id = UUID.randomUUID();
        this.statements.save(
            new StatementEntity(id, validName, description, validEffect, validScope, validMethod, validPath, policy)
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
        validatePolicy(policy);
        existing.description = description;
        existing.effect = validEffect;
        existing.scope = validScope;
        existing.method = validMethod;
        existing.path = validPath;
        existing.policy = policy;
        this.statements.flush();
        return existing.id;
    }

    /// Lists Statements in stable identifier order for offset pagination.
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        return this.list(page, perPage, null);
    }

    /// Lists Statements using an optional database-side object authorization predicate.
    @Transactional(readOnly = true)
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        ObjectAuthorizationService.@Nullable ObjectAuthorizationPlan authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result =
            authorization == null
                ? this.statements.findAllBy(pageable)
                : this.statements.findAll(this.objectAuthorization.specification(authorization), pageable);
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
        if (value == null || value.isBlank()) throw new AuthorizationException(field + " must not be blank");
        return value.trim();
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) throw new AuthorizationException(field + " is required");
        return value;
    }

    private static void validatePolicy(@Nullable String policy) {
        if (policy != null && policy.isBlank()) throw new AuthorizationException("policy must not be blank");
    }
}
