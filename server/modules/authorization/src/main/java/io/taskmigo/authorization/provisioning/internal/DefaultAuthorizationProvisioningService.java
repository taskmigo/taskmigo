package io.taskmigo.authorization.provisioning.internal;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.role.internal.RoleStore.RoleState;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.application.StatementCommandService;
import io.taskmigo.authorization.statement.application.StatementMutationResult;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed authorization state without exposing provisioning semantics through runtime services.
@Service
class DefaultAuthorizationProvisioningService implements AuthorizationProvisioningService {

    private final RoleService roleService;
    private final RoleStore roles;
    private final StatementService statementService;
    private final StatementCommandService statementCommands;

    DefaultAuthorizationProvisioningService(
        RoleService roleService,
        RoleStore roles,
        StatementService statementService,
        StatementCommandService statementCommands
    ) {
        this.roleService = roleService;
        this.roles = roles;
        this.statementService = statementService;
        this.statementCommands = statementCommands;
    }

    @Override
    @Transactional
    public ReconciliationResult<UUID> reconcileStatement(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        StatementMutationResult mutation;
        try {
            mutation = this.statementCommands.reconcileManaged(code, description, effect, scope, method, path, policy);
        } catch (StatementRuleViolation exception) {
            throw badRequest(exception);
        }

        ReconciliationAction action = mutation.created()
            ? ReconciliationAction.ADDED
            : mutation.changed()
              ? ReconciliationAction.UPDATED
              : ReconciliationAction.UNCHANGED;
        return new ReconciliationResult<>(mutation.id(), action);
    }

    @Override
    @Transactional
    public ReconciliationResult<UUID> reconcileRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statementService.requireStatements(requestedIds);

        String validCode = AuthorizationName.requiredRole(code, "code");
        String validDisplayName = AuthorizationName.requiredDisplayName(displayName, "displayName");
        RoleState existing = this.roles.findByCode(validCode).orElse(null);
        if (existing == null) {
            UUID id = this.roleService.createRole(validCode, validDisplayName, description, Set.of());
            this.roles.replaceStatements(id, requestedIds);
            return new ReconciliationResult<>(id, ReconciliationAction.ADDED);
        }

        if (
            existing.displayName().equals(validDisplayName) &&
            Objects.equals(existing.description(), description) &&
            existing.statementIds().equals(requestedIds)
        ) {
            return new ReconciliationResult<>(existing.id(), ReconciliationAction.UNCHANGED);
        }
        this.roles.updateDisplayNameDescriptionAndStatements(
            existing.id(),
            validDisplayName,
            description,
            requestedIds
        );
        return new ReconciliationResult<>(existing.id(), ReconciliationAction.UPDATED);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireStatement(String code) {
        try {
            return this.statementCommands
                .findByCode(code)
                .map(Statement::id)
                .orElseThrow(() ->
                    new AuthorizationProvisioningException("Managed authorization Statement does not exist: " + code)
                );
        } catch (StatementRuleViolation exception) {
            throw badRequest(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireRole(String code) {
        String validCode = AuthorizationName.requiredRole(code, "role reference");
        return this.roles
            .findByCode(validCode)
            .map(RoleState::id)
            .orElseThrow(() ->
                new AuthorizationProvisioningException("Managed authorization Role does not exist: " + validCode)
            );
    }

    @Override
    @Transactional
    public boolean deleteStatement(String code) {
        Statement existing;
        try {
            existing = this.statementCommands.findByCode(code).orElse(null);
        } catch (StatementRuleViolation exception) {
            throw badRequest(exception);
        }
        if (existing == null) {
            return false;
        }
        this.statementCommands.delete(existing);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteRole(String code) {
        String validCode = AuthorizationName.requiredRole(code, "role code");
        Optional<RoleState> existing = this.roles.findByCode(validCode);
        if (existing.isEmpty()) {
            return false;
        }
        this.roles.delete(existing.orElseThrow().id());
        return true;
    }

    private static AuthorizationException badRequest(StatementRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }
}
