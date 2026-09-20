package io.taskmigo.identity.user.application.service;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Implements the User inbound port while keeping persistence and transaction mechanics behind outbound ports.
public final class DefaultUserService implements UserService {

    private final UserQueryRepository users;
    private final SubjectGrantQueryService grantQueries;
    private final SubjectGrantAssignmentService grantAssignments;
    private final TransactionRunner transactions;

    public DefaultUserService(
        UserQueryRepository users,
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.grantQueries = grantQueries;
        this.grantAssignments = grantAssignments;
        this.transactions = transactions;
    }

    @Override
    public UserInfo require(UUID id) {
        return this.transactions.read(() ->
            this.users.find(id).orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"))
        );
    }

    @Override
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.transactions.read(() -> this.users.findForAuthentication(username));
    }

    @Override
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        return this.transactions.read(() -> this.users.list(page, perPage, filter, authorization));
    }

    @Override
    public Set<UUID> roleIds(UUID userId) {
        return this.transactions.read(() -> {
            this.requireExisting(userId);
            return this.grantQueries.roleIds(IdentitySubjects.user(userId));
        });
    }

    @Override
    public void setStatements(UUID userId, Collection<UUID> statementIds) {
        this.transactions.write(() -> {
            this.requireExisting(userId);
            this.grantAssignments.setStatements(IdentitySubjects.user(userId), statementIds);
        });
    }

    @Override
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        this.transactions.write(() -> {
            this.requireExisting(userId);
            this.grantAssignments.setRoles(IdentitySubjects.user(userId), roleIds);
        });
    }

    private void requireExisting(UUID userId) {
        if (!this.users.exists(userId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }
    }
}
