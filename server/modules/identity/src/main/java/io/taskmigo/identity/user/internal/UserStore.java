package io.taskmigo.identity.user.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.query.QueryPredicate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines persistence capabilities required by User application use cases.
public interface UserStore {
    record UserState(
        UUID id,
        String username,
        Set<String> emails,
        String firstName,
        String lastName,
        boolean active,
        @Nullable String passwordHash
    ) {}

    Optional<UserState> find(UUID id);

    Optional<UserState> findByUsername(String username);

    void create(UserState user);

    void updateProfile(UUID id, Set<String> emails, String firstName, String lastName);

    void updatePasswordHash(UUID id, String passwordHash);

    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
}
