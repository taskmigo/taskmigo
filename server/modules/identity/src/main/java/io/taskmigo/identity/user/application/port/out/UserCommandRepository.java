package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.util.Optional;

/// Persists canonical User aggregate state for command use cases.
public interface UserCommandRepository {
    Optional<User> findByUsername(Username username);
    void save(User user);
    void delete(User user);
}
