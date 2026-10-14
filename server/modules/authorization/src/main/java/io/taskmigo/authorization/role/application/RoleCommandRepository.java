package io.taskmigo.authorization.role.application;

import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;

/// Persists canonical Role aggregate state for command use cases.
public interface RoleCommandRepository {
    Optional<Role> find(UUID id);

    Optional<Role> findByCode(RoleCode code);

    void save(Role role);

    void delete(Role role);
}
