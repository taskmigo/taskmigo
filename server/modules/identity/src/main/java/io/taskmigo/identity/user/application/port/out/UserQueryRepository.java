package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.query.QueryPredicate;
import java.util.Optional;
import java.util.UUID;

/// Reads User projections without requiring aggregate hydration.
public interface UserQueryRepository {
    Optional<UserInfo> find(UUID id);
    Optional<UserInfo> findForDelete(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization);
    Optional<UserInfo> findForStatementUpdate(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization);
    Optional<AuthenticationInfo> findForAuthentication(String username);
    boolean exists(UUID id);
    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
}
