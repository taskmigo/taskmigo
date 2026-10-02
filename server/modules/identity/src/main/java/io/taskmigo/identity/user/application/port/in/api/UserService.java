package io.taskmigo.identity.user.application.port.in.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Defines the published inbound port for User queries, lifecycle, and direct grant assignments.
public interface UserService {
    UserInfo require(UUID id);
    Optional<AuthenticationInfo> findForAuthentication(String username);
    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
    Set<UUID> roleIds(UUID userId);
    void setStatements(UUID userId, Collection<UUID> statementIds, UserMutationActor actor);
    boolean setStatements(
        UUID userId,
        Collection<UUID> statementIds,
        ObjectAuthorizationPredicate<UserInfo> authorization,
        UserMutationActor actor
    );
    void setRoles(UUID userId, Collection<UUID> roleIds, UserMutationActor actor);
    boolean delete(UUID userId, ObjectAuthorizationPredicate<UserInfo> authorization, UserMutationActor actor);
}
