package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByUsername(String username);

    List<UserEntity> findTop100ByStatusAndRetainedAtLessThanEqualOrderByRetainedAtAsc(
        UserStatus status,
        Instant retainedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findFirstById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findFirstByUsername(String username);
}
