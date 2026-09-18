package io.taskmigo.authorization.provisioning.internal;

import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.role.internal.RoleStore.RoleState;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.internal.StatementStore;
import java.util.Collection;
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
    private final StatementStore statements;
    private final StatementPolicyValidator policyValidator;

    DefaultAuthorizationProvisioningService(
        RoleService roleService,
        RoleStore roles,
        StatementService statementService,
        StatementStore statements,
        StatementPolicyValidator policyValidator
    ) {
        this.roleService = roleService;
        this.roles = roles;
        this.statementService = statementService;
        this.statements = statements;
        this.policyValidator = policyValidator;
    }

    @Override
    @Transactional
    public UUID reconcileStatement(
        @Nullable String name,
        @Nullable String description,
        @Nullable String effect,
        @Nullable String scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        @Nullable
        Effect parsedEffect = effect == null ? null : Effect.from(effect);
        @Nullable
        Scope parsedScope = scope == null ? null : Scope.from(scope);
        StatementDefinition definition = this.policyValidator.validate(
            name,
            description,
            parsedEffect,
            parsedScope,
            method,
            path,
            policy
        );

        Optional<UUID> existingId = this.statements.findIdByName(definition.name());
        if (existingId.isEmpty()) {
            return this.statements.create(definition);
        }
        UUID id = existingId.orElseThrow();
        this.statements.update(id, definition);
        return id;
    }

    @Override
    @Transactional
    public UUID reconcileRole(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statementService.requireStatements(requestedIds);

        String validName = AuthorizationName.requiredRole(name, "name");
        RoleState existing = this.roles.findByName(validName).orElse(null);
        if (existing == null) {
            UUID id = this.roleService.createRole(validName, description, Set.of());
            this.roles.replaceStatements(id, requestedIds);
            return id;
        }

        this.roles.updateDescriptionAndStatements(existing.id(), description, requestedIds);
        return existing.id();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireStatement(String name) {
        String validName = AuthorizationName.required(name, "statement reference");
        return this.statements
            .findIdByName(validName)
            .orElseThrow(() ->
                new AuthorizationProvisioningException("Managed authorization Statement does not exist: " + validName)
            );
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireRole(String name) {
        String validName = AuthorizationName.requiredRole(name, "role reference");
        return this.roles
            .findByName(validName)
            .map(RoleState::id)
            .orElseThrow(() ->
                new AuthorizationProvisioningException("Managed authorization Role does not exist: " + validName)
            );
    }
}
