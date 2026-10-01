package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
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
    public Optional<User> findByUsername(Username username) {
        return this.users.findByUsername(username.value()).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsernameForUpdate(Username username) {
        return this.users.findFirstByUsername(username.value()).map(UserEntity::toDomain);
    }

    @Override
    public void save(User user) {
        try {
            this.users.saveAndFlush(UserEntity.from(user));
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(UserException.Type.CONFLICT, "Username or email already exists", exception);
        }
    }

    @Override
    public void delete(User user) {
        this.users.deleteById(user.id());
        this.users.flush();
    }
}
