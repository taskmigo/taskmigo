package io.taskmigo.access;

import io.taskmigo.acl.AclPolicyDefinitionCompiler;
import io.taskmigo.acl.AclStatement;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.user.UserService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Owns reusable ACL Statements, Roles that compose Statements, and user-to-Role assignments.
@Service
public class AccessService {

    public static final String PROJECT_MANAGER_ROLE = "project-manager";
    public static final String PROJECT_CREATE_STATEMENT = "project.create";

    private static final String SYSTEM = "SYSTEM";
    private static final String CUSTOM = "CUSTOM";

    private final AclPolicyDefinitionCompiler compiler = new AclPolicyDefinitionCompiler();
    private final StatementRepository statements;
    private final RoleRepository roles;
    private final UserRoleAssignmentRepository userRoles;
    private final OrganizationService organizations;
    private final UserService users;

    AccessService(
        StatementRepository statements,
        RoleRepository roles,
        UserRoleAssignmentRepository userRoles,
        OrganizationService organizations,
        UserService users
    ) {
        this.statements = statements;
        this.roles = roles;
        this.userRoles = userRoles;
        this.organizations = organizations;
        this.users = users;
    }

    /// Creates an organization-owned custom Role from reusable system or organization Statements.
    @Transactional
    public UUID createRole(
        UUID organizationId,
        @Nullable String key,
        @Nullable String name,
        @Nullable String description,
        @Nullable Set<UUID> statementIds
    ) {
        this.organizations.require(organizationId);
        Set<UUID> requestedIds = statementIds == null ? Set.of() : Set.copyOf(statementIds);
        List<StatementEntity> requested = this.requireStatementEntities(requestedIds);
        for (StatementEntity statement : requested) {
            if (!SYSTEM.equals(statement.origin) && !organizationId.equals(statement.organizationId)) {
                throw badRequest("A custom Role can reference only system Statements or Statements from its Organization");
            }
        }

        UUID id = UUID.randomUUID();
        try {
            this.roles.saveAndFlush(
                    new RoleEntity(
                        id,
                        CUSTOM,
                        organizationId,
                        requiredKey(key),
                        required(name, "name"),
                        description,
                        requestedIds
                    )
                );
            return id;
        } catch (DataIntegrityViolationException exception) {
            throw new AccessException(AccessException.Type.CONFLICT, "Role key already exists in the Organization");
        }
    }

    /// Replaces all organization-level Roles assigned directly to a user.
    @Transactional
    public void setUserRoles(UUID userId, @Nullable Set<UUID> roleIds) {
        UserService.UserInfo user = this.users.require(userId);
        Set<UUID> requestedIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        List<RoleEntity> requested = this.requireRoleEntities(requestedIds);
        for (RoleEntity role : requested) {
            if (CUSTOM.equals(role.origin) && !java.util.Objects.equals(user.organizationId(), role.organizationId)) {
                throw badRequest("A user can receive only system Roles or custom Roles from the user's Organization");
            }
        }

        List<UserRoleAssignmentEntity> existing = this.userRoles.findAllByUserId(userId);
        this.userRoles.deleteAll(existing);
        this.userRoles.flush();
        this.userRoles.saveAll(
                requestedIds
                    .stream()
                    .map(roleId -> new UserRoleAssignmentEntity(UUID.randomUUID(), userId, roleId))
                    .toList()
            );
    }

    @Transactional(readOnly = true)
    public UUID roleManagementOrganization(UUID userId) {
        UUID organizationId = this.users.require(userId).organizationId();
        if (organizationId == null) throw badRequest("Organization-level Roles require an Organization-owned user");
        return organizationId;
    }

    @Transactional(readOnly = true)
    public List<RoleInfo> roles(UUID organizationId) {
        this.organizations.require(organizationId);
        List<RoleEntity> result = new ArrayList<>(this.roles.findAllByOriginOrderByKey(SYSTEM));
        result.addAll(this.roles.findAllByOriginAndOrganizationIdOrderByKey(CUSTOM, organizationId));
        return result.stream().map(AccessService::roleInfo).toList();
    }

    @Transactional(readOnly = true)
    public List<StatementInfo> statements(UUID organizationId) {
        this.organizations.require(organizationId);
        List<StatementEntity> result = new ArrayList<>(this.statements.findAllByOriginOrderByKey(SYSTEM));
        result.addAll(this.statements.findAllByOriginAndOrganizationIdOrderByKey(CUSTOM, organizationId));
        return result.stream().map(this::statementInfo).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleInfo> requireRoles(Collection<UUID> ids) {
        return this.requireRoleEntities(ids).stream().map(AccessService::roleInfo).toList();
    }

    @Transactional(readOnly = true)
    public Set<String> statementKeys(Collection<UUID> ids) {
        return this.requireStatementEntities(ids)
            .stream()
            .map(statement -> statement.key)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public Set<String> statementKeysForRoles(Collection<UUID> roleIds) {
        Set<UUID> statementIds = new LinkedHashSet<>();
        for (RoleEntity role : this.requireRoleEntities(roleIds)) statementIds.addAll(role.statementIds);
        return this.statementKeys(statementIds);
    }

    @Transactional(readOnly = true)
    public Set<String> effectiveStatementKeys(UUID userId) {
        Set<UUID> roleIds = this.userRoles
            .findAllByUserId(userId)
            .stream()
            .map(assignment -> assignment.roleId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return this.statementKeysForRoles(roleIds);
    }

    @Transactional(readOnly = true)
    public Set<String> systemStatementKeys() {
        return this.statements
            .findAllByOriginOrderByKey(SYSTEM)
            .stream()
            .map(statement -> statement.key)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /// Returns the Statement catalog visible to an Organization, compiled once for ACL evaluation in the request.
    @Transactional(readOnly = true)
    public List<AclStatement> statementCatalog(@Nullable UUID organizationId) {
        List<StatementEntity> catalog = new ArrayList<>(this.statements.findAllByOriginOrderByKey(SYSTEM));
        if (organizationId != null) {
            catalog.addAll(this.statements.findAllByOriginAndOrganizationIdOrderByKey(CUSTOM, organizationId));
        }
        return catalog.stream().map(statement -> this.compiler.compileStatement(statement.key, statement.definition)).toList();
    }

    @Transactional(readOnly = true)
    public UUID systemRoleId(String key) {
        return this.roles
            .findByOriginAndKey(SYSTEM, key)
            .orElseThrow(() -> badRequest("System Role not found: " + key))
            .id;
    }

    /// Reconciles bootstrap-owned Statements and Roles while preserving stable database IDs across restarts.
    @Transactional
    public void reconcileSystem(
        Map<String, SystemStatementDefinition> statementDefinitions,
        Map<String, SystemRoleDefinition> roleDefinitions
    ) {
        for (var entry : statementDefinitions.entrySet()) {
            this.compiler.compileStatement(entry.getKey(), entry.getValue().definition());
        }

        Map<String, StatementEntity> staleStatements = new LinkedHashMap<>();
        for (StatementEntity statement : this.statements.findAllByOriginOrderByKey(SYSTEM)) {
            staleStatements.put(statement.key, statement);
        }

        Map<String, StatementEntity> desiredStatements = new LinkedHashMap<>();
        for (var entry : statementDefinitions.entrySet()) {
            SystemStatementDefinition definition = entry.getValue();
            StatementEntity statement = staleStatements.remove(entry.getKey());
            if (statement == null) {
                statement = new StatementEntity(
                    UUID.randomUUID(),
                    SYSTEM,
                    null,
                    entry.getKey(),
                    definition.name(),
                    definition.description(),
                    definition.definition()
                );
            } else {
                statement.replace(definition.name(), definition.description(), definition.definition());
            }
            desiredStatements.put(entry.getKey(), statement);
        }
        this.statements.saveAllAndFlush(desiredStatements.values());

        Map<String, RoleEntity> staleRoles = new LinkedHashMap<>();
        for (RoleEntity role : this.roles.findAllByOriginOrderByKey(SYSTEM)) staleRoles.put(role.key, role);

        List<RoleEntity> desiredRoles = new ArrayList<>();
        for (var entry : roleDefinitions.entrySet()) {
            SystemRoleDefinition definition = entry.getValue();
            Set<UUID> statementIds = definition.statementKeys()
                .stream()
                .map(key -> {
                    StatementEntity statement = desiredStatements.get(key);
                    if (statement == null) throw badRequest("System Role references unknown Statement: " + key);
                    return statement.id;
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            RoleEntity role = staleRoles.remove(entry.getKey());
            if (role == null) {
                role = new RoleEntity(
                    UUID.randomUUID(),
                    SYSTEM,
                    null,
                    entry.getKey(),
                    definition.name(),
                    definition.description(),
                    statementIds
                );
            } else {
                role.replace(definition.name(), definition.description(), statementIds);
            }
            desiredRoles.add(role);
        }
        this.roles.saveAllAndFlush(desiredRoles);
        this.roles.deleteAll(staleRoles.values());
        this.roles.flush();
        this.statements.deleteAll(staleStatements.values());
    }

    private List<RoleEntity> requireRoleEntities(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<RoleEntity> found = this.roles.findAllByIdIn(ids);
        if (found.size() != Set.copyOf(ids).size()) throw badRequest("One or more Roles do not exist");
        return found;
    }

    private List<StatementEntity> requireStatementEntities(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<StatementEntity> found = this.statements.findAllByIdIn(ids);
        if (found.size() != Set.copyOf(ids).size()) throw badRequest("One or more Statements do not exist");
        return found;
    }

    private StatementInfo statementInfo(StatementEntity statement) {
        AclStatement compiled = this.compiler.compileStatement(statement.key, statement.definition);
        return new StatementInfo(
            statement.id,
            statement.origin,
            statement.organizationId,
            statement.key,
            statement.name,
            statement.description,
            compiled.mode().name().toLowerCase(Locale.ROOT)
        );
    }

    private static RoleInfo roleInfo(RoleEntity role) {
        return new RoleInfo(
            role.id,
            role.origin,
            role.organizationId,
            role.key,
            role.name,
            role.description,
            Set.copyOf(role.statementIds)
        );
    }

    private static String requiredKey(@Nullable String value) {
        return required(value, "key").toLowerCase(Locale.ROOT);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw badRequest(field + " is required");
        return value.trim();
    }

    private static AccessException badRequest(String message) {
        return new AccessException(AccessException.Type.BAD_REQUEST, message);
    }

    public record StatementInfo(
        UUID id,
        String origin,
        @Nullable UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        String mode
    ) {}

    public record RoleInfo(
        UUID id,
        String origin,
        @Nullable UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        Set<UUID> statementIds
    ) {}

    public record SystemStatementDefinition(
        String name,
        @Nullable String description,
        Map<String, Object> definition
    ) {
        public SystemStatementDefinition {
            definition = Map.copyOf(definition);
        }
    }

    public record SystemRoleDefinition(String name, @Nullable String description, Set<String> statementKeys) {
        public SystemRoleDefinition {
            statementKeys = Set.copyOf(statementKeys);
        }
    }
}
