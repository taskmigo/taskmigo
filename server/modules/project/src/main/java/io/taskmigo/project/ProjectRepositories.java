package io.taskmigo.project;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@SuppressWarnings("ClassNameDiffersFromFileName")
interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    @Modifying
    @Query("delete from ProjectEntity project where project.status = :status and project.archivedAt < :cutoff")
    int deleteArchivedBefore(@Param("status") ProjectStatus status, @Param("cutoff") Instant cutoff);
}

@SuppressWarnings("ClassNameDiffersFromFileName")
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
