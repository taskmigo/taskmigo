package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Group;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Role;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Persists canonical authorization resources and assignments while enforcing graph and organization invariants.
@Service
public class AuthorizationResourceService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private final AuthorizationCompiler compiler;
    private final AuthorizationStatementRepository statements;
    private final AuthorizationFieldRuleRepository fieldRules;
    private final AuthorizationRoleRepository roles;
    private final AuthorizationGroupRepository groups;
    private final AuthorizationRoleStatementRepository roleStatements;
    private final AuthorizationRoleInheritanceRepository roleInheritance;
    private final AuthorizationGroupStatementRepository groupStatements;
    private final AuthorizationGroupInheritanceRepository groupInheritance;
    private final AuthorizationUserStatementRepository userStatements;
    private final AuthorizationUserRoleRepository userRoles;
    private final AuthorizationUserRepository users;

    AuthorizationResourceService(
        AuthorizationCompiler compiler,
        AuthorizationStatementRepository statements,
        AuthorizationFieldRuleRepository fieldRules,
        AuthorizationRoleRepository roles,
        AuthorizationGroupRepository groups,
        AuthorizationRoleStatementRepository roleStatements,
        AuthorizationRoleInheritanceRepository roleInheritance,
        AuthorizationGroupStatementRepository groupStatements,
        AuthorizationGroupInheritanceRepository groupInheritance,
        AuthorizationUserStatementRepository userStatements,
        AuthorizationUserRoleRepository userRoles,
        AuthorizationUserRepository users
    ) {
        this.compiler = compiler;
        this.statements = statements;
        this.fieldRules = fieldRules;
        this.roles = roles;
        this.groups = groups;
        this.roleStatements = roleStatements;
        this.roleInheritance = roleInheritance;
        this.groupStatements = groupStatements;
        this.groupInheritance = groupInheritance;
        this.userStatements = userStatements;
        this.userRoles = userRoles;
        this.users = users;
    }

    /// Validates and idempotently reconciles a Statement within its trusted origin and organization scope.
    @Transactional
    public UUID upsertStatement(@Nullable UUID organizationId, Statement resource, Origin origin) {
        validateScope(organizationId, origin);
        this.compiler.compile(resource, origin);
        ensureKeyAvailableForScope(
            organizationId,
            resource.key(),
            this.statements.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(),
            origin
        );
        AuthorizationStatementEntity entity = this.findExactStatement(organizationId, resource.key()).orElseGet(() ->
            new AuthorizationStatementEntity(UUID.randomUUID(), organizationId, resource, origin)
        );
        if (entity.origin != origin) throw new IllegalArgumentException(
            "Authorization resource origin cannot be changed"
        );
        entity.replace(resource, origin);
        this.statements.save(entity);
        this.fieldRules.deleteAllByStatementId(entity.id);
        this.fieldRules.flush();
        List<AuthorizationResource.FieldRule> fields = orEmpty(resource.fields());
        this.fieldRules.saveAll(
            fields
                .stream()
                .map(field -> new AuthorizationFieldRuleEntity(entity.id, field))
                .toList()
        );
        return entity.id;
    }

    /// Reconciles a Role and atomically replaces its Statement and Role inheritance edges.
    @Transactional
    public UUID upsertRole(@Nullable UUID organizationId, Role resource, Origin origin) {
        validateScope(organizationId, origin);
        validateKey(resource.key());
        ensureKeyAvailableForScope(
            organizationId,
            resource.key(),
            this.roles.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(),
            origin
        );
        List<AuthorizationStatementEntity> referencedStatements = this.resolveStatements(
            organizationId,
            origin,
            orEmpty(resource.statements())
        );
        List<AuthorizationRoleEntity> includedRoles = this.resolveRoles(
            organizationId,
            origin,
            orEmpty(resource.roles())
        );
        AuthorizationRoleEntity entity = this.findExactRole(organizationId, resource.key()).orElseGet(() ->
            new AuthorizationRoleEntity(UUID.randomUUID(), organizationId, resource, origin)
        );
        if (entity.origin != origin) throw new IllegalArgumentException(
            "Authorization resource origin cannot be changed"
        );
        this.ensureRoleGraphAcyclic(organizationId, entity.id, entity.key, includedRoles);
        entity.replace(resource, origin);
        this.roles.save(entity);
        this.roleStatements.deleteAllByRoleId(entity.id);
        this.roleInheritance.deleteAllByRoleId(entity.id);
        this.roleStatements.flush();
        this.roleInheritance.flush();
        this.roleStatements.saveAll(
            referencedStatements
                .stream()
                .map(statement -> new AuthorizationRoleStatementEdge(entity.id, statement.id))
                .toList()
        );
        this.roleInheritance.saveAll(
            includedRoles
                .stream()
                .map(role -> new AuthorizationRoleInheritanceEdge(entity.id, role.id))
                .toList()
        );
        return entity.id;
    }

    /// Reconciles a Group and atomically replaces its Statement and Group inheritance edges.
    @Transactional
    public UUID upsertGroup(@Nullable UUID organizationId, Group resource, Origin origin) {
        validateScope(organizationId, origin);
        validateKey(resource.key());
        ensureKeyAvailableForScope(
            organizationId,
            resource.key(),
            this.groups.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(),
            origin
        );
        List<AuthorizationStatementEntity> referencedStatements = this.resolveStatements(
            organizationId,
            origin,
            orEmpty(resource.statements())
        );
        List<AuthorizationGroupEntity> includedGroups = this.resolveGroups(
            organizationId,
            origin,
            orEmpty(resource.groups())
        );
        AuthorizationGroupEntity entity = this.findExactGroup(organizationId, resource.key()).orElseGet(() ->
            new AuthorizationGroupEntity(UUID.randomUUID(), organizationId, resource, origin)
        );
        if (entity.origin != origin) throw new IllegalArgumentException(
            "Authorization resource origin cannot be changed"
        );
        this.ensureGroupGraphAcyclic(organizationId, entity.id, entity.key, includedGroups);
        entity.replace(resource, origin);
        this.groups.save(entity);
        this.groupStatements.deleteAllByGroupId(entity.id);
        this.groupInheritance.deleteAllByGroupId(entity.id);
        this.groupStatements.flush();
        this.groupInheritance.flush();
        this.groupStatements.saveAll(
            referencedStatements
                .stream()
                .map(statement -> new AuthorizationGroupStatementEdge(entity.id, statement.id))
                .toList()
        );
        this.groupInheritance.saveAll(
            includedGroups
                .stream()
                .map(group -> new AuthorizationGroupInheritanceEdge(entity.id, group.id))
                .toList()
        );
        return entity.id;
    }

    @Transactional
    public void assignStatement(UUID userId, String statementKey) {
        AuthorizationUserEntity user = this.requireUser(userId);
        AuthorizationStatementEntity statement = this.requireRelevantStatement(user.organizationId, statementKey);
        requireAssignableScope(user.organizationId, statement.organizationId);
        if (!this.userStatements.existsByUserIdAndStatementId(userId, statement.id)) {
            this.userStatements.save(new AuthorizationUserStatementAssignment(userId, statement.id));
        }
    }

    @Transactional
    public void assignRole(UUID userId, String roleKey) {
        AuthorizationUserEntity user = this.requireUser(userId);
        AuthorizationRoleEntity role = this.requireRelevantRole(user.organizationId, roleKey);
        requireAssignableScope(user.organizationId, role.organizationId);
        if (!this.userRoles.existsByUserIdAndRoleId(userId, role.id)) {
            this.userRoles.save(new AuthorizationUserRoleAssignment(userId, role.id));
        }
    }

    @Transactional
    public void assignGroup(UUID userId, String groupKey) {
        AuthorizationUserEntity user = this.requireUser(userId);
        AuthorizationGroupEntity group = this.requireRelevantGroup(user.organizationId, groupKey);
        requireAssignableScope(user.organizationId, group.organizationId);
        if (group.memberIds.add(userId)) this.groups.save(group);
    }

    private List<AuthorizationStatementEntity> resolveStatements(
        @Nullable UUID organizationId,
        Origin origin,
        List<String> keys
    ) {
        return keys
            .stream()
            .distinct()
            .map(key -> this.requireStatementForResource(organizationId, origin, key))
            .toList();
    }

    private List<AuthorizationRoleEntity> resolveRoles(
        @Nullable UUID organizationId,
        Origin origin,
        List<String> keys
    ) {
        return keys
            .stream()
            .distinct()
            .map(key -> this.requireRoleForResource(organizationId, origin, key))
            .toList();
    }

    private List<AuthorizationGroupEntity> resolveGroups(
        @Nullable UUID organizationId,
        Origin origin,
        List<String> keys
    ) {
        return keys
            .stream()
            .distinct()
            .map(key -> this.requireGroupForResource(organizationId, origin, key))
            .toList();
    }

    private AuthorizationStatementEntity requireStatementForResource(
        @Nullable UUID organizationId,
        Origin origin,
        String key
    ) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.statements
            .findByOrganizationIdIsNullAndKey(key)
            .orElseThrow(() -> unknown("Statement", key));
        return this.requireRelevantStatement(organizationId, key);
    }

    private AuthorizationRoleEntity requireRoleForResource(@Nullable UUID organizationId, Origin origin, String key) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.roles
            .findByOrganizationIdIsNullAndKey(key)
            .orElseThrow(() -> unknown("Role", key));
        return this.requireRelevantRole(organizationId, key);
    }

    private AuthorizationGroupEntity requireGroupForResource(@Nullable UUID organizationId, Origin origin, String key) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.groups
            .findByOrganizationIdIsNullAndKey(key)
            .orElseThrow(() -> unknown("Group", key));
        return this.requireRelevantGroup(organizationId, key);
    }

    private AuthorizationStatementEntity requireRelevantStatement(@Nullable UUID organizationId, String key) {
        if (organizationId != null) {
            var custom = this.statements.findByOrganizationIdAndKey(organizationId, key);
            if (custom.isPresent()) return custom.orElseThrow();
        }
        return this.statements.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Statement", key));
    }

    private AuthorizationRoleEntity requireRelevantRole(@Nullable UUID organizationId, String key) {
        if (organizationId != null) {
            var custom = this.roles.findByOrganizationIdAndKey(organizationId, key);
            if (custom.isPresent()) return custom.orElseThrow();
        }
        return this.roles.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Role", key));
    }

    private AuthorizationGroupEntity requireRelevantGroup(@Nullable UUID organizationId, String key) {
        if (organizationId != null) {
            var custom = this.groups.findByOrganizationIdAndKey(organizationId, key);
            if (custom.isPresent()) return custom.orElseThrow();
        }
        return this.groups.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Group", key));
    }

    private void ensureRoleGraphAcyclic(
        @Nullable UUID organizationId,
        UUID currentId,
        String currentKey,
        List<AuthorizationRoleEntity> included
    ) {
        List<AuthorizationRoleEntity> allRoles = this.relevantRoles(organizationId);
        Set<UUID> ids = allRoles
            .stream()
            .map(role -> role.id)
            .collect(java.util.stream.Collectors.toSet());
        ids.add(currentId);
        java.util.Map<UUID, List<UUID>> edges = this.groupedRoleEdges(ids);
        edges.put(
            currentId,
            included
                .stream()
                .map(role -> role.id)
                .toList()
        );
        java.util.Map<UUID, String> names = new HashMap<>();
        allRoles.forEach(role -> names.put(role.id, role.key));
        names.put(currentId, currentKey);
        ensureAcyclic(currentId, edges, names, "Role");
    }

    private void ensureGroupGraphAcyclic(
        @Nullable UUID organizationId,
        UUID currentId,
        String currentKey,
        List<AuthorizationGroupEntity> included
    ) {
        List<AuthorizationGroupEntity> allGroups = this.relevantGroups(organizationId);
        Set<UUID> ids = allGroups
            .stream()
            .map(group -> group.id)
            .collect(java.util.stream.Collectors.toSet());
        ids.add(currentId);
        java.util.Map<UUID, List<UUID>> edges = this.groupedGroupEdges(ids);
        edges.put(
            currentId,
            included
                .stream()
                .map(group -> group.id)
                .toList()
        );
        java.util.Map<UUID, String> names = new HashMap<>();
        allGroups.forEach(group -> names.put(group.id, group.key));
        names.put(currentId, currentKey);
        ensureAcyclic(currentId, edges, names, "Group");
    }

    private java.util.Map<UUID, List<UUID>> groupedRoleEdges(Set<UUID> ids) {
        java.util.Map<UUID, List<UUID>> result = new HashMap<>();
        this.roleInheritance
            .findAllByRoleIdIn(ids)
            .forEach(edge ->
                result.computeIfAbsent(edge.roleId, ignored -> new ArrayList<>()).add(edge.includedRoleId)
            );
        return result;
    }

    private java.util.Map<UUID, List<UUID>> groupedGroupEdges(Set<UUID> ids) {
        java.util.Map<UUID, List<UUID>> result = new HashMap<>();
        this.groupInheritance
            .findAllByGroupIdIn(ids)
            .forEach(edge ->
                result.computeIfAbsent(edge.groupId, ignored -> new ArrayList<>()).add(edge.includedGroupId)
            );
        return result;
    }

    private static void ensureAcyclic(
        UUID root,
        java.util.Map<UUID, List<UUID>> edges,
        java.util.Map<UUID, String> names,
        String type
    ) {
        visit(root, edges, names, type, new HashSet<>(), new HashSet<>(), new ArrayDeque<>());
    }

    private static void visit(
        UUID node,
        java.util.Map<UUID, List<UUID>> edges,
        java.util.Map<UUID, String> names,
        String type,
        Set<UUID> visited,
        Set<UUID> active,
        ArrayDeque<UUID> path
    ) {
        if (active.contains(node)) {
            List<String> cycle = path
                .stream()
                .map(id -> names.getOrDefault(id, id.toString()))
                .toList();
            throw new IllegalArgumentException(
                type +
                    " inheritance cycle: " +
                    String.join(" -> ", cycle) +
                    " -> " +
                    names.getOrDefault(node, node.toString())
            );
        }
        if (!visited.add(node)) return;
        active.add(node);
        path.addLast(node);
        for (UUID child : edges.getOrDefault(node, List.of())) visit(child, edges, names, type, visited, active, path);
        path.removeLast();
        active.remove(node);
    }

    private List<AuthorizationRoleEntity> relevantRoles(@Nullable UUID organizationId) {
        return organizationId == null
            ? this.roles.findAllByOrganizationIdIsNullOrderByKey()
            : this.roles.findRelevant(organizationId);
    }

    private List<AuthorizationGroupEntity> relevantGroups(@Nullable UUID organizationId) {
        return organizationId == null
            ? this.groups.findAllByOrganizationIdIsNullOrderByKey()
            : this.groups.findRelevant(organizationId);
    }

    private AuthorizationUserEntity requireUser(UUID userId) {
        return this.users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown User: " + userId));
    }

    private java.util.Optional<AuthorizationStatementEntity> findExactStatement(
        @Nullable UUID organizationId,
        String key
    ) {
        return organizationId == null
            ? this.statements.findByOrganizationIdIsNullAndKey(key)
            : this.statements.findByOrganizationIdAndKey(organizationId, key);
    }

    private java.util.Optional<AuthorizationRoleEntity> findExactRole(@Nullable UUID organizationId, String key) {
        return organizationId == null
            ? this.roles.findByOrganizationIdIsNullAndKey(key)
            : this.roles.findByOrganizationIdAndKey(organizationId, key);
    }

    private java.util.Optional<AuthorizationGroupEntity> findExactGroup(@Nullable UUID organizationId, String key) {
        return organizationId == null
            ? this.groups.findByOrganizationIdIsNullAndKey(key)
            : this.groups.findByOrganizationIdAndKey(organizationId, key);
    }

    private static void ensureKeyAvailableForScope(
        @Nullable UUID organizationId,
        String key,
        boolean systemExists,
        Origin origin
    ) {
        validateKey(key);
        if (origin == Origin.CUSTOM && systemExists) throw new IllegalArgumentException(
            "Custom authorization resource cannot shadow system key: " + key
        );
        validateScope(organizationId, origin);
    }

    private static void validateScope(@Nullable UUID organizationId, Origin origin) {
        if (origin == Origin.SYSTEM && organizationId != null) throw new IllegalArgumentException(
            "System authorization resources are global"
        );
        if (origin == Origin.CUSTOM && organizationId == null) throw new IllegalArgumentException(
            "Custom authorization resources require an organization"
        );
    }

    private static void validateKey(@Nullable String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) throw new IllegalArgumentException(
            "Invalid authorization resource key: " + key
        );
    }

    private static void requireAssignableScope(
        @Nullable UUID userOrganizationId,
        @Nullable UUID resourceOrganizationId
    ) {
        if (
            resourceOrganizationId != null && !resourceOrganizationId.equals(userOrganizationId)
        ) throw new IllegalArgumentException("Authorization resource belongs to another organization");
    }

    private static <T> List<T> orEmpty(@Nullable List<T> values) {
        return values == null ? List.of() : values;
    }

    private static IllegalArgumentException unknown(String type, String key) {
        return new IllegalArgumentException("Unknown " + type + " authorization resource: " + key);
    }
}
