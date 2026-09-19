package io.taskmigo.identity.group;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the application contract for Group use cases.
public interface GroupService {
    OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    );
    UUID create(@Nullable String code, @Nullable String displayName, @Nullable String description);
    UUID create(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        @Nullable Collection<UUID> childGroupIds,
        @Nullable Collection<UUID> roleIds
    );
    void addMember(UUID groupId, UUID userId);
    UUID reconcile(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    );
    void setGroupsForUser(UUID userId, Collection<UUID> groupIds);
    void deleteByCode(String code);
    void requireGroups(Collection<UUID> ids);
    List<UUID> groupsForUser(UUID userId);
    List<RoleInfo> effectiveRolesForUser(UUID userId);
    void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds);
    void setRoles(UUID groupId, Collection<UUID> roleIds);
    List<RoleInfo> effectiveRoles(UUID groupId);
}
