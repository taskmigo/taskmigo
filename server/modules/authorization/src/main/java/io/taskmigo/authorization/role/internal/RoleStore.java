package io.taskmigo.authorization.role.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleHierarchy;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines persistence capabilities required by Role application services without exposing JPA types.
public interface RoleStore {

    record RoleState(UUID id, String name, @Nullable String description, Set<UUID> statementIds, Set<UUID> childIds) {}

    List<RoleState> loadAllForUpdate();

    Optional<RoleState> find(UUID id);

    Optional<RoleState> findByName(String name);

    boolean containsAll(Collection<UUID> ids);

    void create(RoleState role);

    void replaceChildren(UUID roleId, Set<UUID> childIds);

    void replaceStatements(UUID roleId, Set<UUID> statementIds);

    void updateDescriptionAndStatements(UUID roleId, @Nullable String description, Set<UUID> statementIds);

    void replaceClosure(Collection<RoleState> roles, RoleHierarchy hierarchy);

    OffsetPage<RoleInfo> list(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    );

    List<UUID> descendantRoleIds(Collection<UUID> ancestorRoleIds);

    List<RoleInfo> findByIds(Collection<UUID> ids);
}
