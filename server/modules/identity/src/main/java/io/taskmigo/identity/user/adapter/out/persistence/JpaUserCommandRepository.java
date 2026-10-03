package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/// Adapts canonical User aggregate persistence and per-User mutation locking to JPA.
@Repository
public class JpaUserCommandRepository implements UserCommandRepository {

    private final JpaUserRepository users;

    public JpaUserCommandRepository(JpaUserRepository users) {
        this.users = users;
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
    public List<UUID> retainedBefore(Instant cutoff) {
        return this.users
            .findTop100ByStatusAndRetainedAtLessThanEqualOrderByRetainedAtAsc(
                UserStatus.RETAINED,
                cutoff
            )
            .stream()
            .map(UserEntity::id)
            .toList();
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
