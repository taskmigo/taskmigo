package io.taskmigo.auth.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    /// Loads users and their resource fields in one bounded lookup for authorization projection.
    @EntityGraph(attributePaths = {"emails", "roleIds", "statementIds"})
    List<UserEntity> findAllByIdIn(Collection<UUID> ids);

    Optional<UserEntity> findByUsername(String username);
}
