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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByUsername(String username);

    List<UserEntity> findTop100ByStatusAndRetainedAtLessThanEqualOrderByRetainedAtAscIdAsc(
        UserStatus status,
        Instant retainedAt
    );

    @Query(
        value = """
        select *
        from users
        where status = 'RETAINED'
          and retained_at <= :cutoff
          and (
              retained_at > :afterRetainedAt
              or (retained_at = :afterRetainedAt and id > :afterId)
          )
        order by retained_at asc, id asc
        limit 100
        """,
        nativeQuery = true
    )
    List<UserEntity> findRetainedCandidatesAfter(
        @Param("cutoff") Instant cutoff,
        @Param("afterRetainedAt") Instant afterRetainedAt,
        @Param("afterId") UUID afterId
    );

    @Query(
        value = """
        select *
        from users
        where id = :id
          and status = 'RETAINED'
        for update skip locked
        """,
        nativeQuery = true
    )
    Optional<UserEntity> findRetainedByIdForUpdateSkipLocked(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findFirstById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findFirstByUsername(String username);
}
