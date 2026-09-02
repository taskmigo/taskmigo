package io.taskmigo.auth.role;

import io.taskmigo.auth.authorization.condition.AuthorizationName;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages the authorization Statements directly assigned to Roles.
@Service
public class RoleAuthorizationService {

    private final RoleService roles;
    private final RoleRepository roleRepository;

    RoleAuthorizationService(RoleService roles, RoleRepository roleRepository) {
        this.roles = roles;
        this.roleRepository = roleRepository;
    }

    /// Reconciles a managed Role by stable name and replaces its direct Statement assignments.
    @Transactional
    public UUID reconcile(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        String validName = AuthorizationName.requiredRole(name, "name");
        RoleEntity role = this.roleRepository.findByName(validName).orElse(null);
        UUID id;
        if (role == null) {
            id = this.roles.createRole(validName, description, Set.of());
            role = this.roleRepository.findById(id).orElseThrow();
        } else {
            id = role.id;
            role.description = description;
        }
        role.statementIds.clear();
        role.statementIds.addAll(Set.copyOf(statementIds));
        this.roleRepository.flush();
        return id;
    }

    /// Replaces the Statements directly assigned to a Role.
    @Transactional
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        RoleEntity role = this.roleRepository
            .findById(roleId)
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
        role.statementIds.clear();
        role.statementIds.addAll(Set.copyOf(statementIds));
        this.roleRepository.flush();
    }
}
