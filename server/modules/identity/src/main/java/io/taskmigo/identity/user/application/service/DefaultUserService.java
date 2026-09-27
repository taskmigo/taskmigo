package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditMutationEvent;
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
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.query.QueryPredicate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Implements the User inbound port while keeping persistence and transaction mechanics behind outbound ports.
public final class DefaultUserService implements UserService {

    private final UserQueryRepository users;
    private final SubjectGrantQueryService grantQueries;
    private final SubjectGrantAssignmentService grantAssignments;
    private final AuditEventService auditEvents;
    private final TransactionRunner transactions;

    public DefaultUserService(
        UserQueryRepository users,
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        AuditEventService auditEvents,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.grantQueries = grantQueries;
        this.grantAssignments = grantAssignments;
        this.auditEvents = auditEvents;
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
        this.replaceStatements(userId, statementIds, Optional.empty());
    }

    @Override
    public void setStatements(UUID userId, Collection<UUID> statementIds, AuditActor actor) {
        this.replaceStatements(userId, statementIds, Optional.of(actor));
    }

    @Override
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        this.transactions.write(() -> {
            this.requireExisting(userId);
            this.grantAssignments.setRoles(IdentitySubjects.user(userId), roleIds);
        });
    }

    private void replaceStatements(UUID userId, Collection<UUID> statementIds, Optional<AuditActor> actor) {
        this.transactions.write(() -> {
            this.requireExisting(userId);
            SubjectRef subject = IdentitySubjects.user(userId);
            Set<UUID> requested = Set.copyOf(statementIds);
            Set<UUID> previous = actor.isPresent() ? this.grantQueries.statementIds(subject) : Set.of();

            this.grantAssignments.setStatements(subject, requested);

            actor
                .filter(__ -> !previous.equals(requested))
                .ifPresent(current ->
                    this.auditEvents.publish(
                        AuditMutationEvent.user(
                            UUID.randomUUID(),
                            userId,
                            current,
                            Instant.now(),
                            List.of(
                                AuditFieldChange.visible("statementIds", orderedIds(previous), orderedIds(requested))
                            )
                        )
                    )
                );
        });
    }

    private void requireExisting(UUID userId) {
        if (!this.users.exists(userId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }
    }

    private static List<String> orderedIds(Collection<UUID> ids) {
        return ids.stream().sorted().map(UUID::toString).toList();
    }
}
