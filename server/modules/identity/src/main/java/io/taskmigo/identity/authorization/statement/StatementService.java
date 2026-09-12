package io.taskmigo.identity.authorization.statement;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.persistence.statement.StatementEntity;
import io.taskmigo.identity.persistence.statement.StatementRepository;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages canonical authorization Statements and validates their compiled policy modules.
@Service
public class StatementService {

    private final StatementRepository statements;
    private final StatementPolicyValidator policyValidator;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    StatementService(
        StatementRepository statements,
        StatementPolicyValidator policyValidator,
        QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder
    ) {
        this.statements = statements;
        this.policyValidator = policyValidator;
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
        StatementDefinition definition = this.policyValidator.validate(
            name,
            description,
            effect,
            scope,
            method,
            path,
            policy
        );
        String validName = definition.name();
        if (this.statements.existsByName(validName)) {
            throw new AuthorizationException("Statement name already exists");
        }
        UUID id = UUID.randomUUID();
        this.statements.save(new StatementEntity(id, definition));
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
        StatementDefinition definition = this.policyValidator.validate(
            name,
            description,
            effect,
            scope,
            method,
            path,
            policy
        );
        StatementEntity existing = this.statements.findByName(definition.name()).orElse(null);
        if (existing == null) {
            UUID id = UUID.randomUUID();
            this.statements.save(new StatementEntity(id, definition));
            return id;
        }
        existing.update(definition);
        this.statements.flush();
        return existing.id();
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
        var result = this.statements.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
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
            .map(entity -> entity.id())
            .orElseThrow(() ->
                new IllegalStateException("Built-in authorization Statement reference does not exist: " + name)
            );
    }
}
