package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.application.port.out.GroupQueryRepository;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/// Reads Group projections and existence state directly from JPA.
@Repository
public class JpaGroupQueryRepository implements GroupQueryRepository {

    private final JpaGroupRepository groups;
    private final QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder;

    JpaGroupQueryRepository(
        JpaGroupRepository groups,
        QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder
    ) {
        this.groups = groups;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public boolean exists(UUID id) {
        return this.groups.existsById(id);
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        Set<UUID> requested = Set.copyOf(ids);
        return this.groups.findAllById(requested).size() == requested.size();
    }

    @Override
    public OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.groups.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaGroupQueryRepository::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private static GroupInfo info(GroupEntity group) {
        return info(group, Set.of());
    }

    private static GroupInfo info(GroupEntity group, Set<UUID> ancestors) {
        if (ancestors.contains(group.id())) {
            return new GroupInfo(group.id(), group.code(), group.displayName(), group.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(group.id());
        List<GroupInfo> children = group
            .childGroups()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new GroupInfo(group.id(), group.code(), group.displayName(), group.description(), children);
    }
}
