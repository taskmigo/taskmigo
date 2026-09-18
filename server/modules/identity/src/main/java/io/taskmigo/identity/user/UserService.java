package io.taskmigo.identity.user;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the application contract for User use cases.
public interface UserService {
    UUID create(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds
    );
    UserInfo require(UUID id);
    Optional<AuthenticationInfo> findForAuthentication(String username);
    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
    Set<UUID> roleIds(UUID userId);
    void setStatements(UUID userId, Collection<UUID> statementIds);
    void setRoles(UUID userId, Collection<UUID> roleIds);
}
