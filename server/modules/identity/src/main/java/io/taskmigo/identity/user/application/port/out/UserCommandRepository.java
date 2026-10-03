package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Persists canonical User aggregate state for command use cases.
public interface UserCommandRepository {

    boolean lock(UUID id);

    Optional<User> findByIdForUpdate(UUID id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByUsernameForUpdate(Username username);

    List<UUID> retainedBefore(Instant cutoff);

    void save(User user);

}
