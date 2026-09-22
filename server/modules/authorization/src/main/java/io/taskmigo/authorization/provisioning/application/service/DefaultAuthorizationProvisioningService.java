package io.taskmigo.authorization.provisioning.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningResult;
import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleMutationResult;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
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

/// Reconciles managed Access Control state through canonical Role and Statement application ports.
public final class DefaultAuthorizationProvisioningService implements AuthorizationProvisioningService {

    private final RoleCommandService roleCommands;
    private final RoleHierarchyRepository roleHierarchies;
    private final StatementService statementService;
    private final StatementCommandService statementCommands;
    private final TransactionRunner transactions;

    public DefaultAuthorizationProvisioningService(
        RoleCommandService roleCommands,
        RoleHierarchyRepository roleHierarchies,
        StatementService statementService,
        StatementCommandService statementCommands,
        TransactionRunner transactions
    ) {
        this.roleCommands = roleCommands;
        this.roleHierarchies = roleHierarchies;
        this.statementService = statementService;
        this.statementCommands = statementCommands;
        this.transactions = transactions;
    }

    @Override
    public AuthorizationProvisioningResult<UUID> reconcileStatement(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        return this.transactions.write(() ->
            this.reconcileStatementInTransaction(code, description, effect, scope, method, path, policy)
        );
    }

    @Override
    public AuthorizationProvisioningResult<UUID> reconcileRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        return this.transactions.write(() ->
            this.reconcileRoleInTransaction(code, displayName, description, statementIds)
        );
    }

    @Override
    public UUID requireStatement(String code) {
        return this.transactions.read(() -> this.requireStatementInTransaction(code));
    }

    @Override
    public UUID requireRole(String code) {
        return this.transactions.read(() -> this.requireRoleInTransaction(code));
    }

    @Override
    public boolean deleteStatement(String code) {
        return this.transactions.write(() -> this.deleteStatementInTransaction(code));
    }

    @Override
    public boolean deleteRole(String code) {
        return this.transactions.write(() -> this.deleteRoleInTransaction(code));
    }

    private AuthorizationProvisioningResult<UUID> reconcileStatementInTransaction(
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

    private AuthorizationProvisioningResult<UUID> reconcileRoleInTransaction(
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

    private UUID requireStatementInTransaction(String code) {
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

    private UUID requireRoleInTransaction(String code) {
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

    private boolean deleteStatementInTransaction(String code) {
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

    private boolean deleteRoleInTransaction(String code) {
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
