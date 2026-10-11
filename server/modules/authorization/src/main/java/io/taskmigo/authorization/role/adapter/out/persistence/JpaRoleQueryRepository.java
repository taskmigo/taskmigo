package io.taskmigo.authorization.role.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.out.RoleQueryRepository;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.jpaquery.JpaQuerySpecifications;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads Role projections and binds client and authorization predicates through the list operation schema.
@Repository
public class JpaRoleQueryRepository implements RoleQueryRepository {

    private final RoleRepository roles;
    private final QueryPredicateBinder<RoleInfo, RoleEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> objectBinder;

    JpaRoleQueryRepository(RoleRepository roles, ListRolesQuerySchema schema) {
        this.roles = roles;
        this.queryBinder = new JpaQueryPredicateBinder<>(RoleInfo.class, schema);
        this.objectBinder = new JpaObjectAuthorizationPredicateBinder<>(RoleInfo.class, schema);
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        Set<UUID> requested = Set.copyOf(ids);
        return this.roles.findAllById(requested).size() == requested.size();
    }

    @Override
    public OffsetPage<RoleInfo> list(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        Specification<RoleEntity> authorizationSpec = this.objectBinder.bind(authorization);
        Specification<RoleEntity> clientFilter = this.queryBinder.bind(filter);
        var result = this.roles.findAll(
            JpaQuerySpecifications.authorized(authorizationSpec, clientFilter),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaRoleQueryRepository::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public List<RoleInfo> findByIds(Collection<UUID> ids) {
        return this.roles.findAllByIdIn(ids).stream().map(JpaRoleQueryRepository::info).toList();
    }

    private static RoleInfo info(RoleEntity role) {
        return info(role, Set.of());
    }

    private static RoleInfo info(RoleEntity role, Set<UUID> ancestors) {
        if (ancestors.contains(role.id())) {
            return new RoleInfo(role.id(), role.code(), role.displayName(), role.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(role.id());
        List<RoleInfo> children = role
            .childRoles()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new RoleInfo(role.id(), role.code(), role.displayName(), role.description(), children);
    }
}
