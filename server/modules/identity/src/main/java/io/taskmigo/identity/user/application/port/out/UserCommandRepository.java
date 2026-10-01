package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.util.Optional;
import java.util.UUID;

/// Persists canonical User aggregate state for command use cases.
public interface UserCommandRepository {

    boolean lock(UUID id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByUsernameForUpdate(Username username);

    void save(User user);

    void delete(User user);
}
