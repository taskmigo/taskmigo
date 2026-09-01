package io.taskmigo.authorization;

import io.taskmigo.authorization.EffectiveAuthorization.EffectiveStatement;
import io.taskmigo.authorization.EffectiveAuthorization.Provenance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EffectiveAuthorizationResolver {

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

    EffectiveAuthorizationResolver(
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

    @Transactional(readOnly = true)
    public EffectiveAuthorization resolve(UUID userId) {
        AuthorizationUserEntity user = this.users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown User: " + userId));
        List<AuthorizationStatementEntity> statementEntities = relevantStatements(user.organizationId);
        Set<UUID> statementIds = statementEntities.stream().map(statement -> statement.id).collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<AuthorizationFieldRuleEntity>> fieldsByStatement = new HashMap<>();
        this.fieldRules.findAllByStatementIdIn(statementIds).forEach(field -> fieldsByStatement.computeIfAbsent(field.statementId, ignored -> new ArrayList<>()).add(field));
        Map<UUID, CompiledStatement> compiled = new LinkedHashMap<>();
        for (AuthorizationStatementEntity statement : statementEntities) {
            AuthorizationResource.Statement resource = statement.resource(
                fieldsByStatement.getOrDefault(statement.id, List.of())
            );
            compiled.put(statement.id, this.compiler.compileCached(statement.id, resource, statement.origin));
        }

        List<AuthorizationRoleEntity> roleEntities = relevantRoles(user.organizationId);
        Map<UUID, AuthorizationRoleEntity> roleById = indexRoles(roleEntities);
        Set<UUID> roleIds = roleById.keySet();
        Map<UUID, List<UUID>> roleStatementsByRole = new HashMap<>();
        this.roleStatements.findAllByRoleIdIn(roleIds).forEach(edge -> roleStatementsByRole.computeIfAbsent(edge.roleId, ignored -> new ArrayList<>()).add(edge.statementId));
        Map<UUID, List<UUID>> rolesByRole = new HashMap<>();
        this.roleInheritance.findAllByRoleIdIn(roleIds).forEach(edge -> rolesByRole.computeIfAbsent(edge.roleId, ignored -> new ArrayList<>()).add(edge.includedRoleId));

        List<AuthorizationGroupEntity> groupEntities = relevantGroups(user.organizationId);
        Map<UUID, AuthorizationGroupEntity> groupById = indexGroups(groupEntities);
        Set<UUID> groupIds = groupById.keySet();
        Map<UUID, List<UUID>> groupStatementsByGroup = new HashMap<>();
        this.groupStatements.findAllByGroupIdIn(groupIds).forEach(edge -> groupStatementsByGroup.computeIfAbsent(edge.groupId, ignored -> new ArrayList<>()).add(edge.statementId));
        Map<UUID, List<UUID>> groupsByGroup = new HashMap<>();
        this.groupInheritance.findAllByGroupIdIn(groupIds).forEach(edge -> groupsByGroup.computeIfAbsent(edge.groupId, ignored -> new ArrayList<>()).add(edge.includedGroupId));

        Map<UUID, AccumulatedStatement> effective = new LinkedHashMap<>();
        String userNode = "user:" + userId;
        for (AuthorizationUserStatementAssignment assignment : this.userStatements.findAllByUserId(userId)) {
            add(effective, compiled, assignment.statementId, List.of(userNode, statementNode(compiled, assignment.statementId)));
        }
        for (AuthorizationUserRoleAssignment assignment : this.userRoles.findAllByUserId(userId)) {
            walkRole(assignment.roleId, List.of(userNode), new HashSet<>(), roleById, rolesByRole, roleStatementsByRole, compiled, effective);
        }
        for (AuthorizationGroupEntity group : this.groups.findAllForMember(userId)) {
            if (!groupById.containsKey(group.id)) throw new IllegalStateException("Group membership crosses authorization scope: " + group.key);
            walkGroup(group.id, List.of(userNode), new HashSet<>(), groupById, groupsByGroup, groupStatementsByGroup, compiled, effective);
        }

        return new EffectiveAuthorization(
            effective.values().stream().map(value -> new EffectiveStatement(value.statement(), List.copyOf(value.provenance()))).toList()
        );
    }

    private static void walkRole(
        UUID roleId,
        List<String> prefix,
        Set<UUID> active,
        Map<UUID, AuthorizationRoleEntity> roles,
        Map<UUID, List<UUID>> inheritance,
        Map<UUID, List<UUID>> statementEdges,
        Map<UUID, CompiledStatement> statements,
        Map<UUID, AccumulatedStatement> effective
    ) {
        if (!active.add(roleId)) throw new IllegalStateException("Role authorization graph contains a cycle");
        AuthorizationRoleEntity role = require(roles, roleId, "Role");
        List<String> path = append(prefix, "role:" + role.key);
        for (UUID statementId : statementEdges.getOrDefault(roleId, List.of())) add(effective, statements, statementId, append(path, statementNode(statements, statementId)));
        for (UUID included : inheritance.getOrDefault(roleId, List.of())) walkRole(included, path, active, roles, inheritance, statementEdges, statements, effective);
        active.remove(roleId);
    }

    private static void walkGroup(
        UUID groupId,
        List<String> prefix,
        Set<UUID> active,
        Map<UUID, AuthorizationGroupEntity> groups,
        Map<UUID, List<UUID>> inheritance,
        Map<UUID, List<UUID>> statementEdges,
        Map<UUID, CompiledStatement> statements,
        Map<UUID, AccumulatedStatement> effective
    ) {
        if (!active.add(groupId)) throw new IllegalStateException("Group authorization graph contains a cycle");
        AuthorizationGroupEntity group = require(groups, groupId, "Group");
        List<String> path = append(prefix, "group:" + group.key);
        for (UUID statementId : statementEdges.getOrDefault(groupId, List.of())) add(effective, statements, statementId, append(path, statementNode(statements, statementId)));
        for (UUID included : inheritance.getOrDefault(groupId, List.of())) walkGroup(included, path, active, groups, inheritance, statementEdges, statements, effective);
        active.remove(groupId);
    }

    private static void add(Map<UUID, AccumulatedStatement> effective, Map<UUID, CompiledStatement> statements, UUID statementId, List<String> path) {
        CompiledStatement statement = statements.get(statementId);
        if (statement == null) throw new IllegalStateException("Authorization graph references an unavailable Statement: " + statementId);
        AccumulatedStatement accumulated = effective.computeIfAbsent(statementId, ignored -> new AccumulatedStatement(statement, new ArrayList<>()));
        accumulated.provenance().add(new Provenance(path));
    }

    private static String statementNode(Map<UUID, CompiledStatement> statements, UUID statementId) {
        CompiledStatement statement = statements.get(statementId);
        if (statement == null) throw new IllegalStateException("Authorization graph references an unavailable Statement: " + statementId);
        return "statement:" + statement.key();
    }

    private static <T> T require(Map<UUID, T> values, UUID id, String type) {
        T value = values.get(id);
        if (value == null) throw new IllegalStateException(type + " authorization graph references an unavailable resource: " + id);
        return value;
    }

    private List<AuthorizationStatementEntity> relevantStatements(@Nullable UUID organizationId) {
        return organizationId == null ? this.statements.findAllByOrganizationIdIsNullOrderByKey() : this.statements.findRelevant(organizationId);
    }

    private List<AuthorizationRoleEntity> relevantRoles(@Nullable UUID organizationId) {
        return organizationId == null ? this.roles.findAllByOrganizationIdIsNullOrderByKey() : this.roles.findRelevant(organizationId);
    }

    private List<AuthorizationGroupEntity> relevantGroups(@Nullable UUID organizationId) {
        return organizationId == null ? this.groups.findAllByOrganizationIdIsNullOrderByKey() : this.groups.findRelevant(organizationId);
    }

    private static Map<UUID, AuthorizationRoleEntity> indexRoles(List<AuthorizationRoleEntity> roles) {
        Map<UUID, AuthorizationRoleEntity> result = new LinkedHashMap<>();
        roles.forEach(role -> result.put(role.id, role));
        return result;
    }

    private static Map<UUID, AuthorizationGroupEntity> indexGroups(List<AuthorizationGroupEntity> groups) {
        Map<UUID, AuthorizationGroupEntity> result = new LinkedHashMap<>();
        groups.forEach(group -> result.put(group.id, group));
        return result;
    }

    private static List<String> append(List<String> prefix, String value) {
        List<String> result = new ArrayList<>(prefix.size() + 1);
        result.addAll(prefix);
        result.add(value);
        return List.copyOf(result);
    }

    private record AccumulatedStatement(CompiledStatement statement, List<Provenance> provenance) {}
}
