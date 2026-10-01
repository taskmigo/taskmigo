package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.query.QueryPredicate;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Implements the User inbound port while serializing mutations per persisted User row.
public final class DefaultUserService implements UserService {

    private static final String ENTITY_TYPE = "user";

    private final UserQueryRepository users;
    private final UserCommandService commands;
    private final SubjectGrantQueryService grantQueries;
    private final SubjectGrantAssignmentService grantAssignments;
    private final UserAuditAppender audits;
    private final TransactionRunner transactions;
    private final Clock clock;

    public DefaultUserService(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        UserAuditAppender audits,
        TransactionRunner transactions,
        Clock clock
    ) {
        this.users = users;
        this.commands = commands;
        this.grantQueries = grantQueries;
        this.grantAssignments = grantAssignments;
        this.audits = audits;
        this.transactions = transactions;
        this.clock = clock;
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
    public void setStatements(UUID userId, Collection<UUID> statementIds, UserMutationActor actor) {
        this.transactions.write(() -> {
            this.requireLocked(userId);
            SubjectRef subject = IdentitySubjects.user(userId);
            Set<UUID> before = this.grantQueries.statementIds(subject);
            Set<UUID> after = Set.copyOf(statementIds);
            if (before.equals(after)) {
                return;
            }

            this.grantAssignments.setStatements(subject, after);
            this.append(userId, actor, UserAuditChanges.visible("statementIds", ordered(before), ordered(after)));
        });
    }

    @Override
    public void setRoles(UUID userId, Collection<UUID> roleIds, UserMutationActor actor) {
        this.transactions.write(() -> {
            this.requireLocked(userId);
            SubjectRef subject = IdentitySubjects.user(userId);
            Set<UUID> before = this.grantQueries.roleIds(subject);
            Set<UUID> after = Set.copyOf(roleIds);
            if (before.equals(after)) {
                return;
            }

            this.grantAssignments.setRoles(subject, after);
            this.append(userId, actor, UserAuditChanges.visible("roleIds", ordered(before), ordered(after)));
        });
    }

    private void append(UUID userId, UserMutationActor actor, AuditChange change) {
        this.audits.append(
            new AuditEvent(
                UUID.randomUUID(),
                ENTITY_TYPE,
                userId,
                new AuditActor(actor.id(), actor.username()),
                this.clock.instant(),
                List.of(change)
            )
        );
    }

    private void requireExisting(UUID userId) {
        if (!this.users.exists(userId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }
    }

    private void requireLocked(UUID userId) {
        if (!this.commands.lock(userId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }
    }

    private static List<String> ordered(Collection<UUID> ids) {
        return ids.stream().sorted().map(UUID::toString).toList();
    }
}
