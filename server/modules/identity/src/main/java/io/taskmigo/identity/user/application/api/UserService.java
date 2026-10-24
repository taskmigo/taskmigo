package io.taskmigo.identity.user.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.query.ListQueryRequest;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Defines the published User application API for queries, lifecycle, and direct grant assignments.
public interface UserService {
    UserInfo require(UUID id);
    Optional<UserInfo> find(UUID id);
    Optional<AuthenticationInfo> findForAuthentication(String username);
    OffsetPage<UserInfo> list(
        int page,
        int perPage,
        ListQueryRequest query,
        ObjectAuthorizationPredicate<UserInfo> authorization
    );
    Set<UUID> roleIds(UUID userId);
    void setStatements(UUID userId, Collection<UUID> statementIds, UserMutationActor actor);
    void setStatements(
        UUID userId,
        Collection<UUID> statementIds,
        ObjectAuthorizationPredicate<UserInfo> authorization,
        UserMutationActor actor
    );
    void setRoles(UUID userId, Collection<UUID> roleIds, UserMutationActor actor);
    void delete(UUID userId, ObjectAuthorizationPredicate<UserInfo> authorization, UserMutationActor actor);
}
