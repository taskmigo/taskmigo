package io.taskmigo.identity.group.application.port.in.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines published runtime Group use cases.
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

    void requireGroups(Collection<UUID> ids);

    void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds);

    void setRoles(UUID groupId, Collection<UUID> roleIds);
}
