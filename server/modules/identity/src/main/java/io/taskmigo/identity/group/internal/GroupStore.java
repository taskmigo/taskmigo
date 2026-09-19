package io.taskmigo.identity.group.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.hierarchy.GroupHierarchy;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines persistence capabilities required by Group application use cases.
public interface GroupStore {
    record GroupState(
        UUID id,
        String code,
        String displayName,
        @Nullable String description,
        Set<UUID> memberIds,
        Set<UUID> childIds
    ) {}

    List<GroupState> loadAllForUpdate();

    Optional<GroupState> find(UUID id);

    Optional<GroupState> findByCode(String code);

    boolean containsAll(Collection<UUID> ids);

    void create(GroupState group);

    void updateDisplayNameAndDescription(UUID groupId, String displayName, @Nullable String description);

    void addMember(UUID groupId, UUID userId);

    void removeMember(UUID groupId, UUID userId);

    void delete(UUID groupId);

    List<UUID> groupsForUser(UUID userId);

    List<UUID> descendantGroupIds(Collection<UUID> ancestorGroupIds);

    void replaceChildren(UUID groupId, Set<UUID> childIds);

    void replaceClosure(Collection<GroupState> groups, GroupHierarchy hierarchy);

    OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    );
}
