package io.taskmigo.authorization.persistence.role;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.persistence.HierarchyClosureWriter;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.role.RoleHierarchy;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.role.internal.RoleStore.RoleState;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/// Implements Role persistence operations with JPA entities and repositories.
@Service
public class JpaRoleOperations implements RoleStore {

    private final RoleRepository roles;
    private final HierarchyClosureWriter closureWriter;
    private final QueryPredicateBinder<RoleInfo, RoleEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> objectBinder;

    public JpaRoleOperations(
        RoleRepository roles,
        HierarchyClosureWriter closureWriter,
        QueryPredicateBinder<RoleInfo, RoleEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> objectBinder
    ) {
        this.roles = roles;
        this.closureWriter = closureWriter;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public List<RoleState> loadAllForUpdate() {
        return this.roles.findAllForUpdate().stream().map(JpaRoleOperations::state).toList();
    }

    @Override
    public Optional<RoleState> find(UUID id) {
        return this.roles.findById(id).map(JpaRoleOperations::state);
    }

    @Override
    public Optional<RoleState> findByName(String name) {
        return this.roles.findByName(name).map(JpaRoleOperations::state);
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        return this.roles.findAllByIdIn(ids).size() == ids.size();
    }

    @Override
    public void create(RoleState role) {
        List<RoleEntity> children = this.roles.findAllByIdIn(role.childIds());
        RoleEntity entity = new RoleEntity(role.id(), role.name(), role.description());
        entity.addChildRoles(children);
        entity.replaceStatementIds(role.statementIds());
        this.roles.saveAndFlush(entity);
    }

    @Override
    public void replaceChildren(UUID roleId, Set<UUID> childIds) {
        RoleEntity role = this.roles.findById(roleId).orElseThrow();
        role.replaceChildRoles(this.roles.findAllByIdIn(childIds));
        this.roles.flush();
    }

    @Override
    public void replaceStatements(UUID roleId, Set<UUID> statementIds) {
        RoleEntity role = this.roles.findById(roleId).orElseThrow();
        role.replaceStatementIds(statementIds);
        this.roles.flush();
    }

    @Override
    public void updateDescriptionAndStatements(UUID roleId, @Nullable String description, Set<UUID> statementIds) {
        RoleEntity role = this.roles.findById(roleId).orElseThrow();
        role.updateDescription(description);
        role.replaceStatementIds(statementIds);
        this.roles.flush();
    }

    @Override
    public void replaceClosure(Collection<RoleState> states, RoleHierarchy hierarchy) {
        List<RoleEntity> entities = this.roles.findAllByIdIn(states.stream().map(RoleState::id).toList());
        this.closureWriter.replace(
            entities,
            RoleEntity::id,
            roleId -> hierarchy.reachableFrom(Set.of(roleId)),
            RoleHierarchyClosureEntity::new,
            RoleHierarchyClosureEntity.class
        );
    }

    @Override
    public OffsetPage<RoleInfo> list(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.roles.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaRoleOperations::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public List<UUID> descendantRoleIds(Collection<UUID> ancestorRoleIds) {
        return this.roles.findDescendantRoleIds(ancestorRoleIds);
    }

    @Override
    public List<RoleInfo> findByIds(Collection<UUID> ids) {
        return this.roles.findDistinctByIdIn(ids).stream().map(JpaRoleOperations::info).toList();
    }

    private static RoleInfo info(RoleEntity role) {
        return info(role, Set.of());
    }

    private static RoleInfo info(RoleEntity role, Set<UUID> ancestors) {
        if (ancestors.contains(role.id())) {
            return new RoleInfo(role.id(), role.name(), role.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(role.id());
        List<RoleInfo> children = role
            .childRoles()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new RoleInfo(role.id(), role.name(), role.description(), children);
    }

    private static RoleState state(RoleEntity role) {
        return new RoleState(
            role.id(),
            role.name(),
            role.description(),
            role.statementIds(),
            role.childRoles().stream().map(RoleEntity::id).collect(Collectors.toSet())
        );
    }
}
