package io.taskmigo.identity.user.application.port.in.internal;

import io.taskmigo.identity.user.domain.User;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Identity-internal User command use cases executed inside a caller-owned application transaction.
public interface UserCommandService {
    UUID createRuntime(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    );

    UserMutationResult reconcileManaged(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    );

    boolean lock(UUID id);

    Optional<User> findByUsername(@Nullable String username);

    Optional<User> findByUsernameForUpdate(@Nullable String username);

    void delete(User user);
}
