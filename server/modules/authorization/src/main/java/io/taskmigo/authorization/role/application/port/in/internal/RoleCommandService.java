package io.taskmigo.authorization.role.application.port.in.internal;

import io.taskmigo.authorization.role.domain.Role;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Access-Control-internal Role commands shared by runtime and managed provisioning paths.
public interface RoleCommandService {
    UUID createRuntime(@Nullable String code, @Nullable String displayName, @Nullable String description);

    RoleMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    );

    Optional<Role> findByCode(@Nullable String code);

    void replaceStatements(UUID roleId, Collection<UUID> statementIds);

    void delete(Role role);
}
