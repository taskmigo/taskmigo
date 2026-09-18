package io.taskmigo.authorization.persistence.role;

import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.role.RoleAuthorizationService;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.statement.StatementService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages the authorization Statements directly assigned to Roles.
@Service
public class JpaRoleAuthorizationOperations implements RoleAuthorizationService {

    private final RoleService roles;
    private final StatementService statements;
    private final RoleRepository roleRepository;

    public JpaRoleAuthorizationOperations(
        RoleService roles,
        StatementService statements,
        RoleRepository roleRepository
    ) {
        this.roles = roles;
        this.statements = statements;
        this.roleRepository = roleRepository;
    }

    /// Reconciles a managed Role by stable name and replaces its direct Statement assignments.
    @Transactional
    public UUID reconcile(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        Set<UUID> requestedStatementIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedStatementIds);

        String validName = AuthorizationName.requiredRole(name, "name");
        RoleEntity role = this.roleRepository.findByName(validName).orElse(null);
        UUID id;
        if (role == null) {
            id = this.roles.createRole(validName, description, Set.of());
            role = this.roleRepository.findById(id).orElseThrow();
        } else {
            id = role.id();
            role.updateDescription(description);
        }
        role.replaceStatementIds(requestedStatementIds);
        this.roleRepository.flush();
        return id;
    }

    /// Replaces the Statements directly assigned to a Role.
    @Transactional
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        Set<UUID> requestedStatementIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedStatementIds);
        RoleEntity role = this.roleRepository
            .findById(roleId)
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
        role.replaceStatementIds(requestedStatementIds);
        this.roleRepository.flush();
    }
}
