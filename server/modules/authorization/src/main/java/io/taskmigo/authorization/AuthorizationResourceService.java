package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Group;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Role;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public UUID upsertStatement(@Nullable UUID organizationId, Statement resource, Origin origin) {
        validateScope(organizationId, origin);
        this.compiler.compile(resource, origin);
        ensureKeyAvailableForScope(organizationId, resource.key(), this.statements.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(), origin);
        AuthorizationStatementEntity entity = findExactStatement(organizationId, resource.key())
            .orElseGet(() -> new AuthorizationStatementEntity(UUID.randomUUID(), organizationId, resource, origin));
        if (entity.origin != origin) throw new IllegalArgumentException("Authorization resource origin cannot be changed");
        entity.replace(resource, origin);
        this.statements.save(entity);
        this.fieldRules.deleteAllByStatementId(entity.id);
        @SuppressWarnings("NullAway")
        List<AuthorizationResource.FieldRule> fields = resource.fields() == null ? List.of() : resource.fields();
        this.fieldRules.saveAll(fields.stream().map(field -> new AuthorizationFieldRuleEntity(entity.id, field)).toList());
        return entity.id;
    }

    @Transactional
    public UUID upsertRole(@Nullable UUID organizationId, Role resource, Origin origin) {
        validateScope(organizationId, origin);
        validateKey(resource.key());
        ensureKeyAvailableForScope(organizationId, resource.key(), this.roles.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(), origin);
        List<AuthorizationStatementEntity> referencedStatements = resolveStatements(organizationId, origin, safe(resource.statements()));
        List<AuthorizationRoleEntity> includedRoles = resolveRoles(organizationId, origin, safe(resource.roles()));
        AuthorizationRoleEntity entity = findExactRole(organizationId, resource.key())
            .orElseGet(() -> new AuthorizationRoleEntity(UUID.randomUUID(), organizationId, resource, origin));
        if (entity.origin != origin) throw new IllegalArgumentException("Authorization resource origin cannot be changed");
        ensureRoleGraphAcyclic(organizationId, entity.id, entity.key, includedRoles);
        entity.replace(resource, origin);
        this.roles.save(entity);
        this.roleStatements.deleteAllByRoleId(entity.id);
        this.roleInheritance.deleteAllByRoleId(entity.id);
        this.roleStatements.saveAll(referencedStatements.stream().map(statement -> new AuthorizationRoleStatementEdge(entity.id, statement.id)).toList());
        this.roleInheritance.saveAll(includedRoles.stream().map(role -> new AuthorizationRoleInheritanceEdge(entity.id, role.id)).toList());
        return entity.id;
    }

    @Transactional
    public UUID upsertGroup(@Nullable UUID organizationId, Group resource, Origin origin) {
        validateScope(organizationId, origin);
        validateKey(resource.key());
        ensureKeyAvailableForScope(organizationId, resource.key(), this.groups.findByOrganizationIdIsNullAndKey(resource.key()).isPresent(), origin);
        List<AuthorizationStatementEntity> referencedStatements = resolveStatements(organizationId, origin, safe(resource.statements()));
        List<AuthorizationGroupEntity> includedGroups = resolveGroups(organizationId, origin, safe(resource.groups()));
        AuthorizationGroupEntity entity = findExactGroup(organizationId, resource.key())
            .orElseGet(() -> new AuthorizationGroupEntity(UUID.randomUUID(), organizationId, resource, origin));
        if (entity.origin != origin) throw new IllegalArgumentException("Authorization resource origin cannot be changed");
        ensureGroupGraphAcyclic(organizationId, entity.id, entity.key, includedGroups);
        entity.replace(resource, origin);
        this.groups.save(entity);
        this.groupStatements.deleteAllByGroupId(entity.id);
        this.groupInheritance.deleteAllByGroupId(entity.id);
        this.groupStatements.saveAll(referencedStatements.stream().map(statement -> new AuthorizationGroupStatementEdge(entity.id, statement.id)).toList());
        this.groupInheritance.saveAll(includedGroups.stream().map(group -> new AuthorizationGroupInheritanceEdge(entity.id, group.id)).toList());
        return entity.id;
    }

    @Transactional
    public void assignStatement(UUID userId, String statementKey) {
        AuthorizationUserEntity user = requireUser(userId);
        AuthorizationStatementEntity statement = requireRelevantStatement(user.organizationId, statementKey);
        requireAssignableScope(user.organizationId, statement.organizationId);
        if (!this.userStatements.existsByUserIdAndStatementId(userId, statement.id)) {
            this.userStatements.save(new AuthorizationUserStatementAssignment(userId, statement.id));
        }
    }

    @Transactional
    public void assignRole(UUID userId, String roleKey) {
        AuthorizationUserEntity user = requireUser(userId);
        AuthorizationRoleEntity role = requireRelevantRole(user.organizationId, roleKey);
        requireAssignableScope(user.organizationId, role.organizationId);
        if (!this.userRoles.existsByUserIdAndRoleId(userId, role.id)) {
            this.userRoles.save(new AuthorizationUserRoleAssignment(userId, role.id));
        }
    }

    private List<AuthorizationStatementEntity> resolveStatements(@Nullable UUID organizationId, Origin origin, List<String> keys) {
        return keys.stream().distinct().map(key -> requireStatementForResource(organizationId, origin, key)).toList();
    }

    private List<AuthorizationRoleEntity> resolveRoles(@Nullable UUID organizationId, Origin origin, List<String> keys) {
        return keys.stream().distinct().map(key -> requireRoleForResource(organizationId, origin, key)).toList();
    }

    private List<AuthorizationGroupEntity> resolveGroups(@Nullable UUID organizationId, Origin origin, List<String> keys) {
        return keys.stream().distinct().map(key -> requireGroupForResource(organizationId, origin, key)).toList();
    }

    private AuthorizationStatementEntity requireStatementForResource(@Nullable UUID organizationId, Origin origin, String key) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.statements.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Statement", key));
        return requireRelevantStatement(organizationId, key);
    }

    private AuthorizationRoleEntity requireRoleForResource(@Nullable UUID organizationId, Origin origin, String key) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.roles.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Role", key));
        return requireRelevantRole(organizationId, key);
    }

    private AuthorizationGroupEntity requireGroupForResource(@Nullable UUID organizationId, Origin origin, String key) {
        validateKey(key);
        if (origin == Origin.SYSTEM) return this.groups.findByOrganizationIdIsNullAndKey(key).orElseThrow(() -> unknown("Group", key));
        return requireRelevantGroup(organizationId, key);
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

    private void ensureRoleGraphAcyclic(@Nullable UUID organizationId, UUID currentId, String currentKey, List<AuthorizationRoleEntity> included) {
        List<AuthorizationRoleEntity> allRoles = relevantRoles(organizationId);
        Set<UUID> ids = allRoles.stream().map(role -> role.id).collect(java.util.stream.Collectors.toSet());
        ids.add(currentId);
        Map<UUID, List<UUID>> edges = groupedRoleEdges(ids);
        edges.put(currentId, included.stream().map(role -> role.id).toList());
        Map<UUID, String> names = new HashMap<>();
        allRoles.forEach(role -> names.put(role.id, role.key));
        names.put(currentId, currentKey);
        ensureAcyclic(currentId, edges, names, "Role");
    }

    private void ensureGroupGraphAcyclic(@Nullable UUID organizationId, UUID currentId, String currentKey, List<AuthorizationGroupEntity> included) {
        List<AuthorizationGroupEntity> allGroups = relevantGroups(organizationId);
        Set<UUID> ids = allGroups.stream().map(group -> group.id).collect(java.util.stream.Collectors.toSet());
        ids.add(currentId);
        Map<UUID, List<UUID>> edges = groupedGroupEdges(ids);
        edges.put(currentId, included.stream().map(group -> group.id).toList());
        Map<UUID, String> names = new HashMap<>();
        allGroups.forEach(group -> names.put(group.id, group.key));
        names.put(currentId, currentKey);
        ensureAcyclic(currentId, edges, names, "Group");
    }

    private Map<UUID, List<UUID>> groupedRoleEdges(Set<UUID> ids) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        this.roleInheritance.findAllByRoleIdIn(ids).forEach(edge -> result.computeIfAbsent(edge.roleId, ignored -> new ArrayList<>()).add(edge.includedRoleId));
        return result;
    }

    private Map<UUID, List<UUID>> groupedGroupEdges(Set<UUID> ids) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        this.groupInheritance.findAllByGroupIdIn(ids).forEach(edge -> result.computeIfAbsent(edge.groupId, ignored -> new ArrayList<>()).add(edge.includedGroupId));
        return result;
    }

    private static void ensureAcyclic(UUID root, Map<UUID, List<UUID>> edges, Map<UUID, String> names, String type) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> path = new ArrayDeque<>();
        visit(root, edges, names, type, visited, new HashSet<>(), path);
    }

    private static void visit(UUID node, Map<UUID, List<UUID>> edges, Map<UUID, String> names, String type, Set<UUID> visited, Set<UUID> active, ArrayDeque<UUID> path) {
        if (active.contains(node)) {
            List<String> cycle = path.stream().map(id -> names.getOrDefault(id, id.toString())).toList();
            throw new IllegalArgumentException(type + " inheritance cycle: " + String.join(" -> ", cycle) + " -> " + names.getOrDefault(node, node.toString()));
        }
        if (!visited.add(node)) return;
        active.add(node);
        path.addLast(node);
        for (UUID child : edges.getOrDefault(node, List.of())) visit(child, edges, names, type, visited, active, path);
        path.removeLast();
        active.remove(node);
    }

    private List<AuthorizationRoleEntity> relevantRoles(@Nullable UUID organizationId) {
        return organizationId == null ? this.roles.findAllByOrganizationIdIsNullOrderByKey() : this.roles.findRelevant(organizationId);
    }

    private List<AuthorizationGroupEntity> relevantGroups(@Nullable UUID organizationId) {
        return organizationId == null ? this.groups.findAllByOrganizationIdIsNullOrderByKey() : this.groups.findRelevant(organizationId);
    }

    private AuthorizationUserEntity requireUser(UUID userId) {
        return this.users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown User: " + userId));
    }

    private java.util.Optional<AuthorizationStatementEntity> findExactStatement(@Nullable UUID organizationId, String key) {
        return organizationId == null ? this.statements.findByOrganizationIdIsNullAndKey(key) : this.statements.findByOrganizationIdAndKey(organizationId, key);
    }

    private java.util.Optional<AuthorizationRoleEntity> findExactRole(@Nullable UUID organizationId, String key) {
        return organizationId == null ? this.roles.findByOrganizationIdIsNullAndKey(key) : this.roles.findByOrganizationIdAndKey(organizationId, key);
    }

    private java.util.Optional<AuthorizationGroupEntity> findExactGroup(@Nullable UUID organizationId, String key) {
        return organizationId == null ? this.groups.findByOrganizationIdIsNullAndKey(key) : this.groups.findByOrganizationIdAndKey(organizationId, key);
    }

    private static void ensureKeyAvailableForScope(@Nullable UUID organizationId, String key, boolean systemExists, Origin origin) {
        validateKey(key);
        if (origin == Origin.CUSTOM && systemExists) throw new IllegalArgumentException("Custom authorization resource cannot shadow system key: " + key);
        validateScope(organizationId, origin);
    }

    private static void validateScope(@Nullable UUID organizationId, Origin origin) {
        if (origin == Origin.SYSTEM && organizationId != null) throw new IllegalArgumentException("System authorization resources are global");
        if (origin == Origin.CUSTOM && organizationId == null) throw new IllegalArgumentException("Custom authorization resources require an organization");
    }

    private static void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) throw new IllegalArgumentException("Invalid authorization resource key: " + key);
    }

    private static void requireAssignableScope(@Nullable UUID userOrganizationId, @Nullable UUID resourceOrganizationId) {
        if (resourceOrganizationId != null && !resourceOrganizationId.equals(userOrganizationId)) throw new IllegalArgumentException("Authorization resource belongs to another organization");
    }

    private static IllegalArgumentException unknown(String type, String key) {
        return new IllegalArgumentException("Unknown " + type + " authorization resource: " + key);
    }

    private static List<String> safe(@Nullable List<String> values) {
        return values == null ? List.of() : values;
    }
}
