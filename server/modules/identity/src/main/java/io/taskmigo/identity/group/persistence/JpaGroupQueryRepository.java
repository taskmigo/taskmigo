package io.taskmigo.identity.group.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.application.GroupQueryRepository;
import io.taskmigo.identity.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.jpaquery.JpaQuerySortSpecifications;
import io.taskmigo.jpaquery.JpaQuerySpecifications;
import io.taskmigo.query.ListQuerySpec;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads Group projections and binds client and authorization predicates through the list operation schema.
@Repository
public class JpaGroupQueryRepository implements GroupQueryRepository {

    private final JpaGroupRepository groups;
    private final ListGroupsQuerySchema listSchema;
    private final QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder;

    JpaGroupQueryRepository(JpaGroupRepository groups, ListGroupsQuerySchema schema) {
        this.groups = groups;
        this.listSchema = schema;
        this.queryBinder = new JpaQueryPredicateBinder<>(GroupInfo.class, schema);
        this.objectBinder = new JpaObjectAuthorizationPredicateBinder<>(GroupInfo.class, schema);
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
        ListQuerySpec<GroupInfo> query,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        Sort pageableSort = JpaQuerySortSpecifications.hasSorting(query) ? Sort.unsorted() : Sort.by("id");
        var pageable = PageRequest.of(page - 1, perPage, pageableSort);
        Specification<GroupEntity> authorizationSpec = this.objectBinder.bind(authorization);
        Specification<GroupEntity> clientFilter = this.queryBinder.bind(query.predicate());
        Specification<GroupEntity> ordering = JpaQuerySortSpecifications.bind(query, this.listSchema);
        var result = this.groups.findAll(
            JpaQuerySpecifications.authorized(authorizationSpec, clientFilter).and(ordering),
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
