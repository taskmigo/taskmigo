package io.taskmigo.resource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {}

interface UserRepository extends JpaRepository<UserEntity, UUID> {}

interface GroupRepository extends JpaRepository<GroupEntity, UUID> {
    @Query("select g.id from GroupEntity g join g.members m where m.id = :userId")
    List<UUID> findIdsContainingUser(@Param("userId") UUID userId);
}

interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);
}

interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {}

interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {
    Optional<ProjectMemberEntity> findByProjectIdAndPrincipalTypeAndPrincipalId(
        UUID projectId,
        PrincipalType principalType,
        UUID principalId
    );
    List<ProjectMemberEntity> findAllByProjectIdAndPrincipalTypeAndPrincipalIdIn(
        UUID projectId,
        PrincipalType principalType,
        Collection<UUID> principalIds
    );
}
