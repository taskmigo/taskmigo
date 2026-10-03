package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserTombstoneService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.application.port.out.UserAuditScrubber;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// Permanently removes User PII/access data while preserving the stable tombstone row and audit history.
public final class DefaultUserTombstoneService implements UserTombstoneService {

    private static final String ENTITY_TYPE = "user";
    private static final String UNKNOWN_USER = "Unknown user";

    private final UserCommandService users;
    private final UserAccessRevocationService access;
    private final UserAuditScrubber auditScrubber;
    private final UserAuditAppender audits;

    public DefaultUserTombstoneService(
        UserCommandService users,
        UserAccessRevocationService access,
        UserAuditScrubber auditScrubber,
        UserAuditAppender audits
    ) {
        this.users = users;
        this.access = access;
        this.auditScrubber = auditScrubber;
        this.audits = audits;
    }

    @Override
    public void tombstone(User target, UserMutationActor actor, Instant tombstonedAt) {
        String beforeStatus = target.status().name();
        this.access.revoke(target);
        target.tombstone(tombstonedAt);
        this.users.save(target);

        this.auditScrubber.scrub(target.id());

        List<AuditChange> changes = List.of(
            AuditChange.sensitive("username"),
            AuditChange.sensitive("firstName"),
            AuditChange.sensitive("lastName"),
            AuditChange.sensitive("emails"),
            AuditChange.sensitive("passwordHash"),
            AuditChange.sensitive("roleIds"),
            AuditChange.sensitive("statementIds"),
            AuditChange.sensitive("groupIds"),
            AuditChange.sensitive("sessions"),
            AuditChange.visible("status", beforeStatus, "TOMBSTONE"),
            AuditChange.visible("tombstonedAt", null, tombstonedAt)
        );

        String actorUsername = actor.id().equals(target.id()) ? UNKNOWN_USER : actor.username();
        this.audits.append(
            new AuditEvent(
                UUID.randomUUID(),
                ENTITY_TYPE,
                target.id(),
                new AuditActor(actor.id(), actorUsername),
                tombstonedAt,
                changes
            )
        );
    }
}
