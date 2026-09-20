package io.taskmigo.identity.user.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Implements published User query use cases without coupling reads to aggregate persistence.
@Service
public class DefaultUserService implements UserService {

    private final UserQueryRepository users;
    private final SubjectGrantService grants;

    public DefaultUserService(UserQueryRepository users, SubjectGrantService grants) {
        this.users = users;
        this.grants = grants;
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfo require(UUID id) {
        return this.users.find(id).orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users.findForAuthentication(username);
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        return this.users.list(page, perPage, filter, authorization);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(UUID userId) {
        this.requireExisting(userId);
        return this.grants.roleIds(IdentitySubjects.user(userId));
    }

    @Override
    @Transactional
    public void setStatements(UUID userId, Collection<UUID> statementIds) {
        this.requireExisting(userId);
        this.grants.setStatements(IdentitySubjects.user(userId), statementIds);
    }

    @Override
    @Transactional
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        this.requireExisting(userId);
        this.grants.setRoles(IdentitySubjects.user(userId), roleIds);
    }

    private void requireExisting(UUID userId) {
        if (!this.users.exists(userId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }
    }
}
