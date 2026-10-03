package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.out.RetainedUserCandidate;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Adapts canonical User aggregate persistence and per-User mutation locking to JPA.
@Repository
public class JpaUserCommandRepository implements UserCommandRepository {

    private static final int RETENTION_BATCH_SIZE = 100;
    private static final int SKIP_LOCKED_TIMEOUT = -2;
    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    private final JpaUserRepository users;
    private final EntityManager entityManager;

    public JpaUserCommandRepository(JpaUserRepository users, EntityManager entityManager) {
        this.users = users;
        this.entityManager = entityManager;
    }

    @Override
    public boolean lock(UUID id) {
        return this.users.findFirstById(id).isPresent();
    }

    @Override
    public Optional<User> findByIdForUpdate(UUID id) {
        return this.users.findFirstById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return this.users.findByUsername(username.value()).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsernameForUpdate(Username username) {
        return this.users.findFirstByUsername(username.value()).map(UserEntity::toDomain);
    }

    @Override
    public List<RetainedUserCandidate> retainedCandidates(Instant cutoff, @Nullable RetainedUserCandidate after) {
        Specification<UserEntity> eligible = (root, query, builder) -> {
            var retainedAt = root.<Instant>get("retainedAt");
            var id = root.<UUID>get("id");
            var base = builder.and(
                builder.equal(root.get("status"), UserStatus.RETAINED),
                builder.lessThanOrEqualTo(retainedAt, cutoff)
            );
            if (after == null) {
                return base;
            }
            return builder.and(
                base,
                builder.or(
                    builder.greaterThan(retainedAt, after.retainedAt()),
                    builder.and(
                        builder.equal(retainedAt, after.retainedAt()),
                        builder.greaterThan(id, after.id())
                    )
                )
            );
        };
        var page = PageRequest.of(
            0,
            RETENTION_BATCH_SIZE,
            Sort.by(Sort.Order.asc("retainedAt"), Sort.Order.asc("id"))
        );
        return this.users
            .findAll(eligible, page)
            .getContent()
            .stream()
            .map(user -> new RetainedUserCandidate(user.id(), Objects.requireNonNull(user.retainedAt())))
            .toList();
    }

    @Override
    public Optional<User> claimRetainedForUpdate(UUID id) {
        var builder = this.entityManager.getCriteriaBuilder();
        var query = builder.createQuery(UserEntity.class);
        var root = query.from(UserEntity.class);
        query
            .select(root)
            .where(
                builder.equal(root.get("id"), id),
                builder.equal(root.get("status"), UserStatus.RETAINED)
            );
        return this.entityManager
            .createQuery(query)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(LOCK_TIMEOUT_HINT, SKIP_LOCKED_TIMEOUT)
            .getResultStream()
            .findFirst()
            .map(UserEntity::toDomain);
    }

    @Override
    public void save(User user) {
        try {
            this.users.saveAndFlush(UserEntity.from(user));
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(UserException.Type.CONFLICT, "Username or email already exists", exception);
        }
    }
}
