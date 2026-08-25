package io.taskmigo.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("ClassNameDiffersFromFileName")
interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {}

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
