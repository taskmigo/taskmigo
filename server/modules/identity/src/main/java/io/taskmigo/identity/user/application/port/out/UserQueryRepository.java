package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.query.QueryPredicate;
import java.util.Optional;
import java.util.UUID;

/// Reads User projections and resolves authorized mutation targets.
public interface UserQueryRepository {
    Optional<UserInfo> find(UUID id);
    Optional<User> findForDelete(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization);
    Optional<User> findForStatementUpdate(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization);
    Optional<AuthenticationInfo> findForAuthentication(String username);
    boolean exists(UUID id);
    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
}
