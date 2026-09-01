package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Origin;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity(name = "AuthorizationRoleEntity")
@Table(name = "roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "role_key", nullable = false, length = 128)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_origin", nullable = false, length = 16)
    Origin origin;

    protected AuthorizationRoleEntity() {}

    AuthorizationRoleEntity(UUID id, @Nullable UUID organizationId, AuthorizationResource.Role resource, Origin origin) {
        this.id = id;
        this.organizationId = organizationId;
        this.replace(resource, origin);
    }

    void replace(AuthorizationResource.Role resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
    }
}

@Entity(name = "AuthorizationGroupEntity")
@Table(name = "groups")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "group_key", nullable = false, length = 128)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_origin", nullable = false, length = 16)
    Origin origin;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false)
    Set<UUID> memberIds;

    protected AuthorizationGroupEntity() {}

    AuthorizationGroupEntity(UUID id, @Nullable UUID organizationId, AuthorizationResource.Group resource, Origin origin) {
        this.id = id;
        this.organizationId = organizationId;
        this.memberIds = Set.of();
        this.replace(resource, origin);
    }

    void replace(AuthorizationResource.Group resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
    }
}

@Entity
@Table(name = "authorization_role_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleStatementEdge {
    @Id UUID id;
    @Column(name = "role_id", nullable = false) UUID roleId;
    @Column(name = "statement_id", nullable = false) UUID statementId;
    protected AuthorizationRoleStatementEdge() {}
    AuthorizationRoleStatementEdge(UUID roleId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.roleId = roleId;
        this.statementId = statementId;
    }
}

@Entity
@Table(name = "authorization_role_inheritance")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleInheritanceEdge {
    @Id UUID id;
    @Column(name = "role_id", nullable = false) UUID roleId;
    @Column(name = "included_role_id", nullable = false) UUID includedRoleId;
    protected AuthorizationRoleInheritanceEdge() {}
    AuthorizationRoleInheritanceEdge(UUID roleId, UUID includedRoleId) {
        this.id = UUID.randomUUID();
        this.roleId = roleId;
        this.includedRoleId = includedRoleId;
    }
}

@Entity
@Table(name = "authorization_group_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupStatementEdge {
    @Id UUID id;
    @Column(name = "group_id", nullable = false) UUID groupId;
    @Column(name = "statement_id", nullable = false) UUID statementId;
    protected AuthorizationGroupStatementEdge() {}
    AuthorizationGroupStatementEdge(UUID groupId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.statementId = statementId;
    }
}

@Entity
@Table(name = "authorization_group_inheritance")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupInheritanceEdge {
    @Id UUID id;
    @Column(name = "group_id", nullable = false) UUID groupId;
    @Column(name = "included_group_id", nullable = false) UUID includedGroupId;
    protected AuthorizationGroupInheritanceEdge() {}
    AuthorizationGroupInheritanceEdge(UUID groupId, UUID includedGroupId) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.includedGroupId = includedGroupId;
    }
}

@Entity
@Table(name = "authorization_user_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserStatementAssignment {
    @Id UUID id;
    @Column(name = "user_id", nullable = false) UUID userId;
    @Column(name = "statement_id", nullable = false) UUID statementId;
    protected AuthorizationUserStatementAssignment() {}
    AuthorizationUserStatementAssignment(UUID userId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.statementId = statementId;
    }
}

@Entity
@Table(name = "authorization_user_roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserRoleAssignment {
    @Id UUID id;
    @Column(name = "user_id", nullable = false) UUID userId;
    @Column(name = "role_id", nullable = false) UUID roleId;
    protected AuthorizationUserRoleAssignment() {}
    AuthorizationUserRoleAssignment(UUID userId, UUID roleId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.roleId = roleId;
    }
}

@Entity(name = "AuthorizationUserEntity")
@Table(name = "users")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserEntity {
    @Id UUID id;
    @Nullable @Column(name = "organization_id") UUID organizationId;
    protected AuthorizationUserEntity() {}
}

interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRoleEntity, UUID> {
    Optional<AuthorizationRoleEntity> findByOrganizationIdAndKey(UUID organizationId, String key);
    Optional<AuthorizationRoleEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query("select role from AuthorizationRoleEntity role where role.organizationId = :organizationId or role.organizationId is null order by role.key")
    List<AuthorizationRoleEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationRoleEntity> findAllByOrganizationIdIsNullOrderByKey();
}

interface AuthorizationGroupRepository extends JpaRepository<AuthorizationGroupEntity, UUID> {
    Optional<AuthorizationGroupEntity> findByOrganizationIdAndKey(UUID organizationId, String key);
    Optional<AuthorizationGroupEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query("select group from AuthorizationGroupEntity group where group.organizationId = :organizationId or group.organizationId is null order by group.key")
    List<AuthorizationGroupEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationGroupEntity> findAllByOrganizationIdIsNullOrderByKey();

    @Query("select distinct group from AuthorizationGroupEntity group join group.memberIds memberId where memberId = :userId")
    List<AuthorizationGroupEntity> findAllForMember(@Param("userId") UUID userId);
}

interface AuthorizationRoleStatementRepository extends JpaRepository<AuthorizationRoleStatementEdge, UUID> {
    List<AuthorizationRoleStatementEdge> findAllByRoleIdIn(Iterable<UUID> roleIds);
    void deleteAllByRoleId(UUID roleId);
}

interface AuthorizationRoleInheritanceRepository extends JpaRepository<AuthorizationRoleInheritanceEdge, UUID> {
    List<AuthorizationRoleInheritanceEdge> findAllByRoleIdIn(Iterable<UUID> roleIds);
    void deleteAllByRoleId(UUID roleId);
}

interface AuthorizationGroupStatementRepository extends JpaRepository<AuthorizationGroupStatementEdge, UUID> {
    List<AuthorizationGroupStatementEdge> findAllByGroupIdIn(Iterable<UUID> groupIds);
    void deleteAllByGroupId(UUID groupId);
}

interface AuthorizationGroupInheritanceRepository extends JpaRepository<AuthorizationGroupInheritanceEdge, UUID> {
    List<AuthorizationGroupInheritanceEdge> findAllByGroupIdIn(Iterable<UUID> groupIds);
    void deleteAllByGroupId(UUID groupId);
}

interface AuthorizationUserStatementRepository extends JpaRepository<AuthorizationUserStatementAssignment, UUID> {
    List<AuthorizationUserStatementAssignment> findAllByUserId(UUID userId);
    boolean existsByUserIdAndStatementId(UUID userId, UUID statementId);
}

interface AuthorizationUserRoleRepository extends JpaRepository<AuthorizationUserRoleAssignment, UUID> {
    List<AuthorizationUserRoleAssignment> findAllByUserId(UUID userId);
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
}

interface AuthorizationUserRepository extends JpaRepository<AuthorizationUserEntity, UUID> {}
