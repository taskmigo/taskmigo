package io.taskmigo.authorization.provisioning.application;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningResult;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.application.RoleCommandService;
import io.taskmigo.authorization.role.application.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.RoleMutationResult;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleRuleViolation;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementCommandService;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementMutationResult;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed authorization state through the canonical Role and Statement command paths.
@Service
class DefaultAuthorizationProvisioningService implements AuthorizationProvisioningService {

    private final RoleCommandService roleCommands;
    private final RoleHierarchyRepository roleHierarchies;
    private final StatementService statementService;
    private final StatementCommandService statementCommands;

    DefaultAuthorizationProvisioningService(
        RoleCommandService roleCommands,
        RoleHierarchyRepository roleHierarchies,
        StatementService statementService,
        StatementCommandService statementCommands
    ) {
        this.roleCommands = roleCommands;
        this.roleHierarchies = roleHierarchies;
        this.statementService = statementService;
        this.statementCommands = statementCommands;
    }

    @Override
    @Transactional
    public AuthorizationProvisioningResult<UUID> reconcileStatement(
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
            throw invalidInput(exception);
        }

        return new AuthorizationProvisioningResult<>(
            mutation.id(),
            provisioningChange(mutation.created(), mutation.changed())
        );
    }

    @Override
    @Transactional
    public AuthorizationProvisioningResult<UUID> reconcileRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statementService.requireStatements(requestedIds);

        RoleMutationResult mutation;
        try {
            mutation = this.roleCommands.reconcileManaged(code, displayName, description, requestedIds);
        } catch (RoleRuleViolation exception) {
            throw invalidInput(exception);
        }
        if (mutation.created()) {
            this.roleHierarchies.synchronize(this.roleHierarchies.loadForMutation());
        }
        return new AuthorizationProvisioningResult<>(
            mutation.id(),
            provisioningChange(mutation.created(), mutation.changed())
        );
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
            throw invalidInput(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireRole(String code) {
        try {
            return this.roleCommands
                .findByCode(code)
                .map(Role::id)
                .orElseThrow(() ->
                    new AuthorizationProvisioningException("Managed authorization Role does not exist: " + code)
                );
        } catch (RoleRuleViolation exception) {
            throw invalidInput(exception);
        }
    }

    @Override
    @Transactional
    public boolean deleteStatement(String code) {
        Statement existing;
        try {
            existing = this.statementCommands.findByCode(code).orElse(null);
        } catch (StatementRuleViolation exception) {
            throw invalidInput(exception);
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
        Role existing;
        try {
            existing = this.roleCommands.findByCode(code).orElse(null);
        } catch (RoleRuleViolation exception) {
            throw invalidInput(exception);
        }
        if (existing == null) {
            return false;
        }
        this.roleCommands.delete(existing);
        return true;
    }

    private static AuthorizationProvisioningResult.Change provisioningChange(boolean created, boolean changed) {
        return created
            ? AuthorizationProvisioningResult.Change.CREATED
            : changed
              ? AuthorizationProvisioningResult.Change.UPDATED
              : AuthorizationProvisioningResult.Change.UNCHANGED;
    }

    private static AuthorizationException invalidInput(StatementRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }

    private static AuthorizationException invalidInput(RoleRuleViolation exception) {
        return new AuthorizationException(exception.detail());
    }
}
